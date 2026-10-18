import type { CustomComponent } from '../core/CustomComponentHost';
import type { PaintContext } from '../core/PaintContext';
import { CanvasPaintContext } from './CanvasPaintContext';
import { mapInputPoint, inverseInputPoint, intersectInputClip } from './InputGeometry';

/** Must match RcInput in rc-player-protocol. No new wire opcode or property type. */
export const Input = Object.freeze({ TEXT: 'rc:input/text:v1', CHOICE: 'rc:input/choice:v1',
    VALUE: 1, VALUE_RETURN: 2, KEY: 3, LABEL: 4, MULTILINE: 5, OPTIONS: 6, SUBMIT: 7 });
export interface InputOptions {
    /** Opt in only for authenticated interactive hosts; false for widgets/read-only/demo hosts. */
    editable: boolean;
    /** Retain per card across document replacement. */
    drafts?: Map<string, string>;
    onChange?: (key: string, value: string) => void;
    onSubmit?: (action: string, values: Record<string, string>) => void;
}
interface Entry {
    root: HTMLDivElement;
    control?: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
    component: CustomComponent;
    config: string;
    writable: boolean;
    scope: string;
    shape: string;
    caption?: HTMLSpanElement;
}

/** Native accessible DOM controls positioned by the player's actual canvas transform. */
export class WebInputComponents {
    private entries = new Map<string, Entry>();
    private seen = new Set<string>();
    private scopes: string[] = [];
    private defaults = new Map<string, Map<string, string>>();
    private hitContext: CanvasRenderingContext2D | null = null;
    private drafts: Map<string, string>;
    constructor(private canvas: HTMLCanvasElement, private options: InputOptions) {
        this.drafts = options.drafts ?? new Map();
    }
    beginFrame(): void { this.seen.clear(); this.defaults.clear(); this.scopes = []; }
    /** Component ids and draft keys are local to a nested document instance. */
    withDocument<T>(identity: string, paint: () => T): T {
        this.scopes.push(identity);
        try { return paint(); } finally { this.scopes.pop(); }
    }
    private storeKey(scope: string, key: string): string { return scope ? `@${scope}:${key}` : key; }
    private values(scope: string): Record<string, string> {
        return Object.fromEntries(Array.from(this.defaults.get(scope) ?? []).map(([key, value]) => [key, this.drafts.get(this.storeKey(scope, key)) ?? value]));
    }
    endFrame(): void {
        for (const [id, entry] of this.entries) if (!this.seen.has(id)) {
            entry.root.remove(); this.entries.delete(id);
        }
    }
    dispose(): void { for (const entry of this.entries.values()) entry.root.remove(); this.entries.clear(); }
    draw(id: number, config: string, pc: PaintContext, w: number, h: number, component?: CustomComponent): boolean {
        if (config !== Input.TEXT && config !== Input.CHOICE) return false;
        if (!component || !(pc instanceof CanvasPaintContext)) return false;
        const scope = JSON.stringify(this.scopes);
        const draftScope = this.scopes.length ? scope : '';
        const identity = `${scope}:${id}`;
        this.seen.add(identity);
        const key = component.text(Input.KEY);
        const storeKey = this.storeKey(draftScope, key);
        let defaults = this.defaults.get(draftScope);
        if (!defaults) { defaults = new Map(); this.defaults.set(draftScope, defaults); }
        defaults.set(key, component.text(Input.VALUE));
        const context = pc.getCanvas();
        const matrix = context.getTransform();
        let polygon = [{x:0,y:0},{x:w,y:0},{x:w,y:h},{x:0,y:h}].map(p => mapInputPoint(matrix, p));
        const clip = pc.getInputClip();
        for (const rectangle of clip.rectangles) polygon = intersectInputClip(polygon, rectangle);
        if (clip.rounded.length && !this.hitContext) this.hitContext = document.createElement('canvas').getContext('2d');
        const roundedCovered = clip.rounded.every(path => polygon.every(p => this.hitContext?.isPointInPath(path, p.x, p.y)));
        if (context.canvas !== this.canvas || clip.unsupported || !roundedCovered) {
            this.entries.get(identity)?.root.remove(); this.entries.delete(identity);
            // Arbitrary path clips/offscreen surfaces stay on the canvas, which applies their
            // exact clip. Never place an interactive overlay outside an unrepresentable region.
            context.save(); context.font = '14px system-ui'; context.fillStyle = '#555';
            context.fillText(`${component.text(Input.LABEL)} · Open in app to edit`, 4, 20, Math.max(1, w-8));
            context.restore();
            return true;
        }
        const writable = this.options.editable && !!key.trim() && component.canReturnText(Input.VALUE_RETURN);
        const shape = `${config === Input.TEXT && component.integer(Input.MULTILINE) === 1}:${!!component.text(Input.SUBMIT)}`;
        let entry = this.entries.get(identity);
        let restoreFocus = false;
        if (entry && (entry.shape !== shape || entry.config !== config || entry.writable !== writable || entry.component.text(Input.KEY) !== key)) {
            restoreFocus = entry.control === document.activeElement;
            entry.root.remove(); this.entries.delete(identity); entry = undefined;
        }
        if (!entry) {
            const root = document.createElement('div');
            root.style.cssText = 'position:absolute;transform-origin:0 0;box-sizing:border-box;overflow:auto;font:14px system-ui;background:var(--card-background-color,white);color:var(--primary-text-color,#202124);padding:6px;';
            // Stop editing gestures from also activating canvas actions.
            for (const event of ['pointerdown', 'click', 'keydown']) root.addEventListener(event, e => e.stopPropagation());
            entry = { root, component, config, writable, scope: draftScope, shape };
            this.entries.set(identity, entry);
            this.canvas.parentNode?.appendChild(root);
            if (writable) {
                const label = document.createElement('label');
                const caption = document.createElement('span');
                entry.caption = caption;
                caption.textContent = component.text(Input.LABEL);
                caption.style.display = 'block'; label.appendChild(caption);
                const control = config === Input.CHOICE ? document.createElement('select') :
                    component.integer(Input.MULTILINE) === 1 ? document.createElement('textarea') : document.createElement('input');
                if (control instanceof HTMLTextAreaElement) control.rows = 4;
                control.style.cssText = 'font:inherit;width:100%;box-sizing:border-box;min-height:30px;';
                entry.control = control; label.appendChild(control); root.appendChild(label);
                const current = entry;
                control.addEventListener(config === Input.CHOICE ? 'change' : 'input', () => {
                    if (current.component.returnText(Input.VALUE_RETURN, control.value)) {
                        this.drafts.set(storeKey, control.value); this.options.onChange?.(storeKey, control.value);
                    }
                });
                const submit = () => {
                    const action = current.component.text(Input.SUBMIT);
                    if (!action) return;
                    this.options.onSubmit?.(action, { ...this.values(current.scope), [key]: control.value });
                    control.blur();
                };
                control.addEventListener('keydown', event => {
                    const e = event as KeyboardEvent;
                    if (e.key === 'Enter' && !e.isComposing && control instanceof HTMLInputElement) {
                        e.preventDefault(); submit();
                    }
                });
                if (component.text(Input.SUBMIT)) {
                    const button = document.createElement('button'); button.textContent = 'Send';
                    button.addEventListener('click', submit); root.appendChild(button);
                }
            }
        }
        entry.component = component;
        if (entry.caption) entry.caption.textContent = component.text(Input.LABEL);
        const value = this.drafts.get(storeKey) ?? component.text(Input.VALUE);
        if (entry.control) {
            if (entry.control instanceof HTMLSelectElement) {
                const options = [...new Set(component.text(Input.OPTIONS).split('\n').filter(Boolean))];
                if (Array.from(entry.control.options).map(o => o.value).join('\n') !== options.join('\n')) {
                    entry.control.replaceChildren(...options.map(value => {
                        const option = document.createElement('option'); option.value = value; option.textContent = value; return option;
                    }));
                }
            }
            // Avoid assigning .value when unchanged: preserve caret, selection and IME composition.
            if (entry.control.value !== value) entry.control.value = value;
            if (component.text(Input.VALUE) !== value) component.returnText(Input.VALUE_RETURN, value);
        } else entry.root.textContent = `${component.text(Input.LABEL)}: ${value} · Open in app to edit`;
        if (restoreFocus) entry.control?.focus();
        const m = matrix;
        entry.root.hidden = !polygon.length || context.globalAlpha <= 0;
        entry.root.style.opacity = String(context.globalAlpha);
        if (entry.root.hidden) entry.control?.blur();
        entry.root.style.clipPath = polygon.length ? `polygon(${polygon.map(p => { const local = inverseInputPoint(m, p); return `${local.x}px ${local.y}px`; }).join(',')})` : 'inset(100%)';
        const sx = this.canvas.clientWidth / this.canvas.width, sy = this.canvas.clientHeight / this.canvas.height;
        entry.root.style.left = `${this.canvas.offsetLeft}px`; entry.root.style.top = `${this.canvas.offsetTop}px`;
        entry.root.style.width = `${w}px`; entry.root.style.height = `${h}px`;
        entry.root.style.transform = `matrix(${m.a*sx},${m.b*sy},${m.c*sx},${m.d*sy},${m.e*sx},${m.f*sy})`;
        return true;
    }
}
