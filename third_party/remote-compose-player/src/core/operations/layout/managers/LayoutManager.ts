// LayoutManager: abstract base class for layout managers (Box, Row, Column, Canvas).
// Port of Java LayoutManager.java — handles measurement with fill/wrap/exact sizing.

import { LayoutComponent } from '../LayoutComponent';
import type { PaintContext } from '../../../PaintContext';
import { RemoteContext } from '../../../RemoteContext';
import type { MeasurePass } from '../measure/MeasurePass';
import { Size } from '../measure/Size';
import { WidthModifier, HeightModifier, ScrollModifier, DimensionConstraintsModifier } from '../modifiers/ModifierOperations';
import { isNaNBits, idFromBits } from '../../Utils';
import { PaintOperation } from '../../../PaintOperation';

/**
 * The space left for content inside [size] once [padding] is taken off, never negative.
 *
 * Padding can exceed the component's own size — `.width(20).padding(30)` is legal, and the size
 * modifier is not widened to fit the padding — so the subtraction can go below zero. Compose gives
 * such a component a zero-sized content area; letting a negative reach child measurement instead
 * puts it into text layout and wrap-size accumulation, where it produces negative measured widths
 * rather than an empty one.
 */
function contentExtent(size: number, padding: number): number {
    return Math.max(0, size - padding);
}

export abstract class LayoutManager extends LayoutComponent {
    protected mCachedWrapSize = new Size();
    protected mLastMeasurePass: MeasurePass | null = null;
    protected mLastMinW = -1;
    protected mLastMaxW = -1;
    protected mLastMinH = -1;
    protected mLastMaxH = -1;

    measure(context: PaintContext, minWidth: number, maxWidth: number,
            minHeight: number, maxHeight: number, measure: MeasurePass): void {
        if (this.mLastMeasurePass === measure &&
            this.mLastMinW === minWidth && this.mLastMaxW === maxWidth &&
            this.mLastMinH === minHeight && this.mLastMaxH === maxHeight &&
            !this.mNeedsMeasure) {
            return;
        }
        this.mLastMeasurePass = measure;
        this.mLastMinW = minWidth;
        this.mLastMaxW = maxWidth;
        this.mLastMinH = minHeight;
        this.mLastMaxH = maxHeight;

        // Padding resolves in the modifiers' updateVariables (variables, DP scaling); pick up
        // the resolved values, as Java's computeModifierDefinedWidth does via updatePadding.
        this.updatePadding();
        const selfMeasure = measure.get(this);
        const padding_w = this.mPaddingLeft + this.mPaddingRight;
        const padding_h = this.mPaddingTop + this.mPaddingBottom;

        const wMod = this.getWidthModifier();
        const hMod = this.getHeightModifier();
        // A width/height bound to an expression its parent just re-evaluated (componentWidth()
        // in this component's modifier, re-evaluated in the parent's measure below) resolves before it is
        // read, so the first layout already has it (as C++, which re-runs the data ops and
        // measures again); otherwise the value only lands at paint, a layout late.
        for (const mod of [wMod, hMod]) {
            if (mod && mod.isDirty()) {
                mod.markNotDirty();
                mod.updateVariables(context.getContext());
            }
        }

        // Document dp→px scale (DOC_DENSITY_AT_GENERATION; 1 for density-1 docs), applied to an
        // EXACT_DP size, which WidthModifier/HeightModifier leave as raw dp (see
        // WidthModifier.updateVariables). width/heightIn bounds are NOT scaled here: their
        // modifiers already apply the density in updateVariables, as the reference's
        // DimensionInModifierOperation does, so scaling them again drew a `heightIn(52.dp)`
        // button 4x its dp size at density 2.
        const dp = this.getDpScale(context);

        // widthIn / heightIn tighten the incoming constraints before anything is sized,
        // as BaseModernMeasurePolicy does (minWidth = max(minWidth, widthIn.min),
        // maxWidth = min(maxWidth, widthIn.max)). An explicit width is then clamped into
        // them: `width(40).widthIn(100, 260)` is 100, not 40.
        {
            const wIn = this.getWidthInModifier();
            if (wIn) {
                if (wIn.getMin() >= 0) minWidth = Math.max(minWidth, wIn.getMin());
                if (wIn.getMax() >= 0) maxWidth = Math.min(maxWidth, wIn.getMax());
            }
            const hIn = this.getHeightInModifier();
            if (hIn) {
                if (hIn.getMin() >= 0) minHeight = Math.max(minHeight, hIn.getMin());
                if (hIn.getMax() >= 0) maxHeight = Math.min(maxHeight, hIn.getMax());
            }
        }

        // RowLayout.isInHorizontalFill / ColumnLayout.isInVerticalFill: a row with a
        // horizontally weighted child fills horizontally, a column with a vertically weighted
        // child fills vertically.
        //
        // Local delta: except over an explicit size. The Java core (and so the AOSP view player)
        // fills even past `width(180.dp)`, but Compose — the CMP player and the AndroidX embedded
        // player this one is measured against — applies the exact size as the constraint first,
        // so the weights share that size instead of the whole parent. Filling there laid every
        // Wear split toggle row and button group out full-bleed (renders/rc-cmp-lane-ab: "The
        // AOSP view player lays the split row out full-bleed").
        const isExactW = wMod?.getType() === WidthModifier.EXACT || wMod?.getType() === WidthModifier.EXACT_DP;
        const isExactH = hMod?.getType() === HeightModifier.EXACT || hMod?.getType() === HeightModifier.EXACT_DP;
        const [weightFillAnyW, weightFillAnyH] = this.weightFill();
        const weightFillW = weightFillAnyW && !isExactW;
        const weightFillH = weightFillAnyH && !isExactH;

        // Determine width
        let w: number;
        if (weightFillW && wMod?.getType() !== WidthModifier.FILL) {
            w = maxWidth;
        } else if (wMod && (wMod.getType() === WidthModifier.EXACT || wMod.getType() === WidthModifier.EXACT_DP)) {
            // EXACT is already in px; EXACT_DP is raw dp and needs the document's
            // generation density applied before it can be compared with anything else.
            const exactW = wMod.getType() === WidthModifier.EXACT_DP
                ? wMod.getValue() * dp
                : wMod.getValue();
            // Clamp to the incoming constraint. Without this a child larger than its
            // parent keeps its requested size and overflows; the reference and the C++
            // player both clamp (BaseModernMeasurePolicy: min(measuredWidth, maxWidth)).
            w = Math.min(exactW + this.mPadBeforeWidth, maxWidth);
        } else if (wMod && wMod.getType() === WidthModifier.FILL) {
            // A fill may carry a fraction of the parent; a bare fill carries NaN.
            w = wMod.hasFraction() ? maxWidth * wMod.getValue() : maxWidth;
        } else if (wMod && wMod.getType() === WidthModifier.WEIGHT) {
            // A weighted child gets its share from the parent's distribution pass. Until
            // then its own size is just its modifier-defined size, as in the reference
            // (max(measured, computeModifierDefinedWidth)). Defaulting to maxWidth here
            // leaks the full width whenever no distribution happens — a weight on the
            // cross axis, or in a parent that wraps and so has no slack to share.
            //
            // But the distribution pass communicates the share it decided on *as the
            // incoming constraint*: RowLayout re-measures each weighted child with
            // `minWidth == maxWidth == childWidth`. Taking the modifier-defined size
            // unconditionally throws that away and re-measures the child at ~0, which is
            // silent — the child is then laid out and painted at that width, so a text
            // inside it wraps one word per line and centres itself around a zero-width
            // box, i.e. off its own component. Honour the constraint the parent handed
            // down and fall back to the modifier-defined size only when it is loose.
            w = Math.min(Math.max(this.mPadBeforeWidth, minWidth), maxWidth);
        } else {
            // WRAP or other — compute from children
            w = maxWidth; // temporary, will be adjusted by computeWrapSize
        }

        // Determine height
        let h: number;
        if (weightFillH && hMod?.getType() !== HeightModifier.FILL) {
            h = maxHeight;
        } else if (hMod && (hMod.getType() === HeightModifier.EXACT || hMod.getType() === HeightModifier.EXACT_DP)) {
            const exactH = hMod.getType() === HeightModifier.EXACT_DP
                ? hMod.getValue() * dp
                : hMod.getValue();
            h = Math.min(exactH + this.mPadBeforeHeight, maxHeight);
        } else if (hMod && hMod.getType() === HeightModifier.FILL) {
            h = hMod.hasFraction() ? maxHeight * hMod.getValue() : maxHeight;
        } else if (hMod && hMod.getType() === HeightModifier.WEIGHT) {
            // See the width branch: a tight incoming constraint is the parent's
            // distributed share and wins over the modifier-defined size.
            h = Math.min(Math.max(this.mPadBeforeHeight, minHeight), maxHeight);
        } else {
            h = maxHeight;
        }

        selfMeasure.setW(w);
        selfMeasure.setH(h);

        // A weight is not a wrap, even before the parent has distributed its share:
        // BaseModernMeasurePolicy sizes it to its modifier-defined size (0 for a bare
        // weight) and measures its children against that. Measuring it as a wrap made a
        // weighted column holding a fillMaxWidth child take the whole row in the row's
        // first computeSize pass, so a fixed spacer after it was offered 0 and collapsed.
        const horizontalWrap = !weightFillW && wMod?.getType() === WidthModifier.WRAP;
        const verticalWrap = !weightFillH && hMod?.getType() === HeightModifier.WRAP;

        if (horizontalWrap || verticalWrap) {
            this.mCachedWrapSize.clear();
            // Children must be measured against *this* component's resolved size, not
            // against the space it was offered. The reference tightens the inset to the
            // measured size for any non-wrapping axis (BaseModernMeasurePolicy: "non-WRAP
            // gets exact inset"), and C++ does the same. Without it a box with an explicit
            // width measures its children at the parent's full width first, and a
            // wrapping height then locks in from that wrong measurement — which is why
            // text in a narrow fixed-width box was sized to one line and never re-grew.
            //
            // A weighted axis is the exception: InsetWrapMeasurePolicy tightens only in its
            // fixed/wrap branch, so a weight keeps the offered inset (maxWidth - padding).
            // Its own size there is just its modifier-defined size (0 for a bare weight),
            // and `0 - padding` would measure the children at a negative width: a weighted
            // column with start padding then wrapped to 0 high and was pinned at that by the
            // row's weight distribution (T12).
            const weightW = wMod?.getType() === WidthModifier.WEIGHT;
            const weightH = hMod?.getType() === HeightModifier.WEIGHT;
            const childMaxW = contentExtent(horizontalWrap || weightW ? maxWidth : w, padding_w);
            const childMaxH = contentExtent(verticalWrap || weightH ? maxHeight : h, padding_h);
            this.computeWrapSize(context, minWidth, childMaxW, minHeight,
                childMaxH, horizontalWrap, verticalWrap, measure, this.mCachedWrapSize);

            if (horizontalWrap) {
                w = this.mCachedWrapSize.getWidth() + padding_w;
                // Apply WidthIn constraints
                const wIn = this.getWidthInModifier();
                if (wIn) {
                    if (wIn.getMin() >= 0) w = Math.max(w, wIn.getMin());
                    if (wIn.getMax() >= 0) w = Math.min(w, wIn.getMax());
                }
                w = Math.min(w, maxWidth);
            }
            if (verticalWrap) {
                h = this.mCachedWrapSize.getHeight() + padding_h;
                const hIn = this.getHeightInModifier();
                if (hIn) {
                    if (hIn.getMin() >= 0) h = Math.max(h, hIn.getMin());
                    if (hIn.getMax() >= 0) h = Math.min(h, hIn.getMax());
                }
                h = Math.min(h, maxHeight);
            }

            selfMeasure.setW(w);
            selfMeasure.setH(h);
        }

        // Scroll-aware measurement (matching Java LayoutManager.measure_v1_1_0):
        // Re-measure children with unbounded dimension on the scroll axis to discover
        // full content size, then store scroll dimensions for variable writing.
        const scrollMod = this.getScrollModifier();
        if (scrollMod) {
            const isVertical = (scrollMod.getDirection() === ScrollModifier.VERTICAL);
            const hostW = contentExtent(Math.min(w, maxWidth), padding_w);
            const hostH = contentExtent(Math.min(h, maxHeight), padding_h);
            const unboundW = isVertical ? hostW : 1e9;
            const unboundH = isVertical ? 1e9 : hostH;

            this.mCachedWrapSize.clear();
            this.computeWrapSize(context, 0, unboundW, 0, unboundH,
                true, true, measure, this.mCachedWrapSize);

            if (isVertical) {
                this.mScrollHostDimension = hostH;
                this.mScrollContentDimension = this.mCachedWrapSize.getHeight();
                // The modifier needs these too: they are what bound a direct-mode drag.
                // Without them its max stays 0 and dragging is clamped to nothing, which
                // looks exactly like scrolling not being implemented.
                scrollMod.setVerticalScrollDimension(
                    this.mScrollHostDimension, this.mScrollContentDimension);
            } else {
                this.mScrollHostDimension = hostW;
                this.mScrollContentDimension = this.mCachedWrapSize.getWidth();
                scrollMod.setHorizontalScrollDimension(
                    this.mScrollHostDimension, this.mScrollContentDimension);
            }

            // Re-measure children with unbounded content dimension
            const childMaxW = isVertical ? contentExtent(w, padding_w)
                : Math.max(contentExtent(w, padding_w), this.mScrollContentDimension);
            const childMaxH = isVertical ? Math.max(contentExtent(h, padding_h), this.mScrollContentDimension)
                : contentExtent(h, padding_h);
            this.computeSize(context, 0, childMaxW, 0, childMaxH, measure);
        }

        // Update ComponentValue float bindings with our final dimensions
        // so LAYOUT_COMPUTE expressions can reference parent width/height.
        // Java does this via ComponentData.updateComponentData in LayoutComponent.
        this.updateComponentValues(context.getContext(), w, h);
        // Java LayoutManager.updateComponentValues(PaintContext, ...): then the operations
        // reading those values (a FloatExpression of componentHeight()) re-evaluate at once,
        // before the children measure against them.
        const ctx = context.getContext();
        for (const op of this.mContentOps) {
            if (op instanceof PaintOperation) continue;
            if (op.isDirty() && typeof (op as any).updateVariables === 'function') {
                op.markNotDirty();
                (op as any).updateVariables(ctx);
                op.apply(ctx);
            }
        }

        // Measure children with fill sizing (skip if already done in the scroll path, or by
        // computeWrapSize for a wrapping axis).
        // The children's minimums are 0, not this container's own `minWidth`/`minHeight`: a min
        // constraint bounds *this* component (a weighted cell is handed min == max so it fills its
        // slot), and forwarding it downward made every child at least as large as the parent — a
        // `size(20)` icon and a wrapped label both came out full-cell and overlapped their
        // neighbours. Compose measures a Row/Column/Box child with a zero minimum for the same
        // reason. It also kept `ColumnLayout.computeSize` from ever shrinking: that loop reduces
        // the child *max* as it goes, so with a fixed min the second child was measured min > max
        // and every later child inherited the parent's height. The scroll path above already
        // passes 0.
        if (!scrollMod && !(horizontalWrap || verticalWrap)) {
            this.computeSize(context, 0, contentExtent(w, padding_w),
                0, contentExtent(h, padding_h), measure);
        }

        // Re-assign final dimensions after computeSize() (matching Java lines 558-563).
        // Subclass computeSize() overrides (e.g. CoreText) may overwrite selfMeasure
        // with content-only dimensions; restore the container's computed w/h here.
        w = Math.max(w, minWidth);
        h = Math.max(h, minHeight);
        // EnforceConstraintsMeasurePolicy (the default measure version) then clamps to the
        // max, so a widthIn minimum larger than the space offered still gives way to it.
        w = Math.min(w, maxWidth);
        h = Math.min(h, maxHeight);
        // ...and a *required* constraint (DimensionConstraints REQUIRED_*) wins over that
        // (DimensionConstraintsModifierOperation.applyWidthConstraint).
        const wReq = this.getWidthInModifier();
        if (wReq instanceof DimensionConstraintsModifier
            && wReq.getType() === DimensionConstraintsModifier.REQUIRED_HORIZONTAL) {
            w = Math.max(wReq.getMin(), Math.min(w, wReq.getMax()));
        }
        const hReq = this.getHeightInModifier();
        if (hReq instanceof DimensionConstraintsModifier
            && hReq.getType() === DimensionConstraintsModifier.REQUIRED_VERTICAL) {
            h = Math.max(hReq.getMin(), Math.min(h, hReq.getMax()));
        }
        selfMeasure.setW(w);
        selfMeasure.setH(h);

        // Run internal layout measure (positioning children)
        this.internalLayoutMeasure(context, measure);
    }

    layout(context: RemoteContext, measure: MeasurePass): void {
        // super.layout() already recurses into children, so we only add
        // layoutModifiers here (passes dimensions to Border/Background decorators).
        super.layout(context, measure);
        const self = measure.get(this);
        this.layoutModifiers(self.getW(), self.getH());

        // Write scroll max/notch variables to context
        // (matching Java ScrollModifierOperation.layout())
        const scrollMod = this.getScrollModifier();
        if (scrollMod) {
            const maxScroll = Math.max(0, this.mScrollContentDimension - this.mScrollHostDimension);
            const maxNan = scrollMod.getMaxNan();
            const notchNan = scrollMod.getNotchMaxNan();
            if (isNaNBits(maxNan)) {
                context.loadFloat(idFromBits(maxNan), maxScroll);
            }
            if (isNaNBits(notchNan)) {
                context.loadFloat(idFromBits(notchNan), this.mScrollContentDimension);
            }
        }
    }

    /** Whether the children's weights make this fill [horizontally, vertically]. */
    protected weightFill(): [boolean, boolean] { return [false, false]; }

    // Override in subclasses to compute wrap-content size
    computeWrapSize(_context: PaintContext, _minWidth: number, _maxWidth: number,
                    _minHeight: number, _maxHeight: number,
                    _horizontalWrap: boolean, _verticalWrap: boolean,
                    _measure: MeasurePass, _size: Size): void { /* override */ }

    // Override in subclasses to measure non-wrap children
    computeSize(_context: PaintContext, _minWidth: number, _maxWidth: number,
                _minHeight: number, _maxHeight: number, _measure: MeasurePass): void { /* override */ }

    // Override in subclasses to position children
    internalLayoutMeasure(_context: PaintContext, _measure: MeasurePass): void { /* override */ }

    /** The document's dp→px scale (DOC_DENSITY_AT_GENERATION). Used to convert
     *  dp-typed dimension bounds to generation pixels. Defaults to 1 (unset/
     *  density-1 documents), so it is a no-op for everything authored today. */
    protected getDpScale(context: PaintContext): number {
        const d = context.getContext().getDensity();
        return (Number.isNaN(d) || d <= 0) ? 1 : d;
    }

    /** dp→px factor for values AndroidX scales *only* under DP density behavior —
     *  layout `spacedBy` spacing here, matching Row/ColumnLayout which multiply the
     *  gap by the density when `getDensityBehavior() == DP`. Returns 1 for LEGACY /
     *  PIXELS behavior so authored-in-px documents are untouched. */
    protected getDpBehaviorScale(context: PaintContext): number {
        const ctx = context.getContext();
        if (ctx.getDensityBehavior() !== RemoteContext.DENSITY_BEHAVIOR_DP) return 1;
        const d = ctx.getDensity();
        return (Number.isNaN(d) || d <= 0) ? 1 : d;
    }

    /**
     * `spacedBy` in physical pixels.
     *
     * Spacing is authored in dp but only scales when the document declares DENSITY_BEHAVIOR_DP
     * — note `== DP`, not the `!= PIXELS` the dimension modifiers use. Under the default
     * LEGACY behaviour the reference does not scale spacing either, so this returns the raw
     * value for almost every document in the corpus.
     *
     * Takes a PaintContext because that is all the layout managers are handed.
     */
    protected spacedByPx(context: PaintContext, spacedBy: number): number {
        if (context.getDensityBehavior?.() !== RemoteContext.DENSITY_BEHAVIOR_DP) {
            return spacedBy;
        }
        const density = context.getDensity();
        return (density > 0 && !Number.isNaN(density)) ? spacedBy * density : spacedBy;
    }
}
