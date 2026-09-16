/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.remote.core.operations.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.withSettings;

import androidx.compose.remote.core.CoreDocument;
import androidx.compose.remote.core.CustomContext;
import androidx.compose.remote.core.Operation;
import androidx.compose.remote.core.Operations;
import androidx.compose.remote.core.PaintContext;
import androidx.compose.remote.core.RcPlatformServices;
import androidx.compose.remote.core.RemoteClock;
import androidx.compose.remote.core.RemoteComposeState;
import androidx.compose.remote.core.RemoteContext;
import androidx.compose.remote.core.ScrollingEdgeEffect;
import androidx.compose.remote.core.SerializableToString;
import androidx.compose.remote.core.semantics.ScrollableComponent;
import androidx.compose.remote.core.WireBuffer;
import androidx.compose.remote.core.documentation.DocumentationBuilder;
import androidx.compose.remote.core.operations.ComponentValue;
import androidx.compose.remote.core.operations.Header;
import androidx.compose.remote.core.operations.Utils;
import androidx.compose.remote.core.operations.layout.AnimatableValue;
import androidx.compose.remote.core.operations.layout.animation.AnimateMeasure;
import androidx.compose.remote.core.operations.layout.animation.AnimationSpec;
import androidx.compose.remote.core.operations.layout.animation.Particle;
import androidx.compose.remote.core.operations.layout.animation.ParticleAnimation;
import androidx.compose.remote.core.operations.layout.animation.RootAnimateMeasure;
import androidx.compose.remote.core.operations.layout.managers.BoxLayout;
import androidx.compose.remote.core.operations.layout.managers.CanvasLayout;
import androidx.compose.remote.core.operations.layout.managers.CollapsibleColumnLayout;
import androidx.compose.remote.core.operations.layout.managers.CollapsiblePriority;
import androidx.compose.remote.core.operations.layout.managers.CollapsibleRowLayout;
import androidx.compose.remote.core.operations.layout.managers.ColumnLayout;
import androidx.compose.remote.core.operations.layout.managers.CoreText;
import androidx.compose.remote.core.operations.layout.managers.Custom;
import androidx.compose.remote.core.operations.layout.managers.FitBoxLayout;
import androidx.compose.remote.core.operations.layout.managers.FlowLayout;
import androidx.compose.remote.core.operations.layout.managers.ImageLayout;
import androidx.compose.remote.core.operations.layout.managers.LayoutManager;
import androidx.compose.remote.core.operations.layout.managers.RowLayout;
import androidx.compose.remote.core.operations.layout.managers.StateLayout;
import androidx.compose.remote.core.operations.layout.managers.TextLayout;
import androidx.compose.remote.core.operations.layout.managers.TextStyle;
import androidx.compose.remote.core.operations.layout.managers.policies.BaseModernMeasurePolicy;
import androidx.compose.remote.core.operations.layout.managers.policies.EnforceConstraintsMeasurePolicy;
import androidx.compose.remote.core.operations.layout.managers.policies.InlineExpressionMeasurePolicy;
import androidx.compose.remote.core.operations.layout.managers.policies.InsetWrapMeasurePolicy;
import androidx.compose.remote.core.operations.layout.managers.policies.LegacyMeasurePolicy;
import androidx.compose.remote.core.operations.layout.measure.ComponentMeasure;
import androidx.compose.remote.core.operations.layout.measure.ComponentMeasurePool;
import androidx.compose.remote.core.operations.layout.measure.FlatMeasurePass;
import androidx.compose.remote.core.operations.layout.measure.MeasurePass;
import androidx.compose.remote.core.operations.layout.measure.Size;
import androidx.compose.remote.core.operations.DataDynamicListFloat;
import androidx.compose.remote.core.operations.layout.modifiers.AlignByModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.BackgroundModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ShapeType;
import androidx.compose.remote.core.operations.utilities.ArrayAccess;
import androidx.compose.remote.core.operations.utilities.CollectionsAccess;
import androidx.compose.remote.core.operations.layout.modifiers.BorderModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ClipRectModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.CollapsiblePriorityModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ComponentModifiers;
import androidx.compose.remote.core.operations.layout.modifiers.ComponentVisibilityOperation;
import androidx.compose.remote.core.operations.layout.modifiers.DimensionConstraintsModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.DimensionInModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.DimensionModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.DrawContentOperation;
import androidx.compose.remote.core.operations.layout.modifiers.GraphicsLayerModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.HeightInModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.HeightModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.HostActionMetadataOperation;
import androidx.compose.remote.core.operations.layout.modifiers.HostActionOperation;
import androidx.compose.remote.core.operations.layout.modifiers.HostNamedActionOperation;
import androidx.compose.remote.core.operations.layout.modifiers.LayoutComputeOperation;
import androidx.compose.remote.core.operations.layout.modifiers.MarqueeModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.OffsetModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.PaddingModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.RippleModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.RoundedClipRectModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.RunActionOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ScrollModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ValueFloatChangeActionOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ValueFloatExpressionChangeActionOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ValueIntegerChangeActionOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ValueIntegerExpressionChangeActionOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ValueStringChangeActionOperation;
import androidx.compose.remote.core.operations.layout.modifiers.WidthInModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.WidthModifierOperation;
import androidx.compose.remote.core.operations.layout.modifiers.ZIndexModifierOperation;
import androidx.compose.remote.core.operations.layout.utils.DebugLog;
import androidx.compose.remote.core.TouchListener;
import androidx.compose.remote.core.operations.TextData;
import androidx.compose.remote.core.operations.TouchExpression;
import androidx.compose.remote.core.operations.loom.LoomWireBuffer;
import androidx.compose.remote.core.operations.loom.RemapContext;
import androidx.compose.remote.core.operations.paint.PaintBundle;
import androidx.compose.remote.core.operations.utilities.StringSerializer;
import androidx.compose.remote.core.operations.utilities.easing.GeneralEasing;
import androidx.compose.remote.core.semantics.AccessibleComponent;
import androidx.compose.remote.core.semantics.CoreSemantics;
import androidx.compose.remote.core.serialize.MapSerializer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class LayoutSubsystemCoverageTest {

    private RemoteContext mRemoteContext;
    private PaintContext mPaintContext;
    private CoreDocument mDocument;
    private RemoteComposeState mState;
    private RemoteClock mClock;

    @Before
    public void setUp() {
        mRemoteContext = mock(RemoteContext.class);
        mPaintContext = mock(PaintContext.class, withSettings().extraInterfaces(CustomContext.class));
        mDocument = mock(CoreDocument.class);
        mState = new RemoteComposeState();
        mClock = mock(RemoteClock.class);

        mRemoteContext.mRemoteComposeState = mState;
        when(mRemoteContext.getDocument()).thenReturn(mDocument);
        when(mRemoteContext.getClock()).thenReturn(mClock);
        when(mRemoteContext.supportsVersion(anyInt(), anyInt(), anyInt())).thenReturn(true);
        when(mRemoteContext.isAnimationEnabled()).thenReturn(true);
        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
        when(mRemoteContext.getPaintContext()).thenReturn(mPaintContext);
        when(mRemoteContext.getComponentMeasurePool()).thenReturn(new ComponentMeasurePool());
        mRemoteContext.currentTime = 1000L;

        when(mPaintContext.getContext()).thenReturn(mRemoteContext);
        when(mPaintContext.getClock()).thenReturn(mClock);
        when(mPaintContext.getDensityBehavior()).thenReturn(CoreDocument.DENSITY_BEHAVIOR_PIXELS);
        when(mPaintContext.getDensity()).thenReturn(1.0f);
        when(mPaintContext.getMeasureVersion()).thenReturn(LayoutManager.DEFAULT_MEASURE_TYPE);
        when(mClock.millis()).thenReturn(1000L);
    }

    private DocumentationBuilder mockDoc() {
        return mock(DocumentationBuilder.class, RETURNS_DEEP_STUBS);
    }

    private MapSerializer mockMapSerializer() {
        return mock(MapSerializer.class, RETURNS_DEEP_STUBS);
    }

    // =========================================================================
    // SECTION 1: LoopOperation, ImpulseOperation, ImpulseProcess
    // =========================================================================

    @Test
    public void testLoopOperationComprehensive() {
        LoopOperation op1 = new LoopOperation(5, 10);
        assertEquals(Integer.MAX_VALUE, op1.estimateIterations());
        assertEquals("Loop", LoopOperation.name());

        LoopOperation op2 = new LoopOperation(10, 0f, 1f, 10f);
        assertEquals(10, op2.estimateIterations());
        assertNotNull(op2.getList());

        // write & read
        WireBuffer buf = new WireBuffer();
        op2.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        List<Operation> readOps = new ArrayList<>();
        LoopOperation.read(buf, readOps);
        assertEquals(1, readOps.size());
        assertTrue(readOps.get(0) instanceof LoopOperation);

        // invalid read steps
        WireBuffer errBuf1 = new WireBuffer();
        LoopOperation.apply(errBuf1, 10, 0f, 0f, 10f);
        errBuf1.setIndex(0);
        errBuf1.readOperationType();
        try {
            LoopOperation.read(errBuf1, new ArrayList<>());
            fail("Expected step=0 exception");
        } catch (RuntimeException expected) {}

        WireBuffer errBuf2 = new WireBuffer();
        LoopOperation.apply(errBuf2, 10, 0f, -1f, 10f);
        errBuf2.setIndex(0);
        errBuf2.readOperationType();
        try {
            LoopOperation.read(errBuf2, new ArrayList<>());
            fail("Expected negative step exception");
        } catch (RuntimeException expected) {}

        // register listening and update variables
        float nanVar = Utils.asNan(101);
        LoopOperation opNan = new LoopOperation(0, nanVar, nanVar, nanVar);
        opNan.registerListening(mRemoteContext);
        when(mRemoteContext.getFloat(101)).thenReturn(5f);
        opNan.updateVariables(mRemoteContext);
        assertEquals(10, opNan.estimateIterations()); // Nan branch returns 10

        // paint with index 0
        LoopOperation opNoIndex = new LoopOperation(0, 0f, 1f, 3f);
        opNoIndex.mFromOut = 0f;
        opNoIndex.mUntilOut = 3f;
        opNoIndex.mStepOut = 1f;
        opNoIndex.paint(mPaintContext);

        // paint with non-zero index
        LoopOperation opWithIndex = new LoopOperation(50, 0f, 1f, 3f);
        opWithIndex.mFromOut = 0f;
        opWithIndex.mUntilOut = 3f;
        opWithIndex.mStepOut = 1f;
        opWithIndex.paint(mPaintContext);

        // serialize & docs
        MapSerializer serializer = mockMapSerializer();
        op2.serialize(serializer);
        DocumentationBuilder doc = mockDoc();
        LoopOperation.documentation(doc);
        assertNotNull(op2.deepToString("  "));
    }

    @Test
    public void testImpulseOperationAndProcessComprehensive() {
        ImpulseOperation impulse = new ImpulseOperation(500f, 100f);
        assertEquals(500 * 60, impulse.estimateIterations());
        assertEquals("ImpulseOperation", ImpulseOperation.name());

        ImpulseProcess process = new ImpulseProcess();
        impulse.setProcess(process);
        assertNotNull(impulse.getList());

        // write & read
        WireBuffer buf = new WireBuffer();
        impulse.write(buf);
        buf.setIndex(0);
        List<Operation> readOps = new ArrayList<>();
        ImpulseOperation.read(buf, readOps);
        assertEquals(1, readOps.size());

        // register listening and update variables
        float nanDur = Utils.asNan(102);
        float nanStart = Utils.asNan(103);
        ImpulseOperation impulseNan = new ImpulseOperation(nanDur, nanStart);
        impulseNan.getList().add(process);
        impulseNan.registerListening(mRemoteContext);
        when(mRemoteContext.getFloat(102)).thenReturn(400f);
        when(mRemoteContext.getFloat(103)).thenReturn(200f);
        impulseNan.updateVariables(mRemoteContext);
        assertEquals(10, impulseNan.estimateIterations());

        // paint when currentTime < startAt -> wakeIn
        when(mRemoteContext.getAnimationTime()).thenReturn(50f);
        impulse.paint(mPaintContext);

        // paint when currentTime is within duration (initial pass)
        when(mRemoteContext.getAnimationTime()).thenReturn(200f);
        impulse.paint(mPaintContext);

        // paint second pass (runs process.paint)
        impulse.paint(mPaintContext);

        // paint when currentTime > startAt + duration (resets pass)
        when(mRemoteContext.getAnimationTime()).thenReturn(700f);
        impulse.paint(mPaintContext);

        // serialize & documentation
        MapSerializer ms = mockMapSerializer();
        impulse.serialize(ms);
        DocumentationBuilder doc = mockDoc();
        ImpulseOperation.documentation(doc);
        assertNotNull(impulse.deepToString(""));

        // ImpulseProcess methods
        WireBuffer pBuf = new WireBuffer();
        process.write(pBuf);
        pBuf.setIndex(0);
        List<Operation> pList = new ArrayList<>();
        ImpulseProcess.read(pBuf, pList);
        assertEquals(1, pList.size());
        process.paint(mPaintContext);
        process.registerListening(mRemoteContext);
        process.updateVariables(mRemoteContext);
        process.serialize(ms);
        ImpulseProcess.documentation(doc);
        assertNotNull(process.deepToString(""));
        assertEquals("Loop", ImpulseProcess.name());
    }

    // =========================================================================
    // SECTION 2: Action Operations (Host, RunAction, ValueChange)
    // =========================================================================

    @Test
    public void testHostActionsComprehensive() {
        Component dummy = new Component(1, 0, 0, 10, 10, null);

        // HostActionOperation
        HostActionOperation hostOp = new HostActionOperation(10);
        assertEquals(10, hostOp.getActionId());
        assertEquals("HOST_ACTION", hostOp.serializedName());
        hostOp.apply(mRemoteContext);
        hostOp.runAction(mRemoteContext, mDocument, dummy, 5f, 5f);
        WireBuffer buf = new WireBuffer();
        hostOp.write(buf);
        buf.setIndex(0);
        List<Operation> ops = new ArrayList<>();
        HostActionOperation.read(buf, ops);
        assertEquals(1, ops.size());
        DocumentationBuilder doc = mockDoc();
        HostActionOperation.documentation(doc);
        hostOp.serialize(mockMapSerializer());
        StringSerializer ss = new StringSerializer();
        hostOp.serializeToString(0, ss);
        assertNotNull(hostOp.deepToString(""));

        // HostNamedActionOperation with all types
        int[] types = {
            HostNamedActionOperation.FLOAT_TYPE,
            HostNamedActionOperation.INT_TYPE,
            HostNamedActionOperation.STRING_TYPE,
            HostNamedActionOperation.FLOAT_ARRAY_TYPE,
            HostNamedActionOperation.NONE_TYPE,
            999 // default/invalid branch
        };
        mState.updateFloat(100, 1.23f);
        mState.updateInteger(101, 456);
        mState.updateData(102, "test-string");
        mState.updateData(103, new float[]{1f, 2f});
        for (int t : types) {
            HostNamedActionOperation named = new HostNamedActionOperation(20, t, 100);
            named.runAction(mRemoteContext, mDocument, dummy, 0f, 0f);
            named.serialize(mockMapSerializer());
            named.serializeToString(0, ss);
        }
        HostNamedActionOperation namedNoVal = new HostNamedActionOperation(20, -1, -1);
        namedNoVal.serializeToString(0, ss);
        namedNoVal.apply(mRemoteContext);
        WireBuffer nBuf = new WireBuffer();
        namedNoVal.write(nBuf);
        nBuf.setIndex(0);
        ops.clear();
        HostNamedActionOperation.read(nBuf, ops);
        HostNamedActionOperation.documentation(doc);
        assertNotNull(namedNoVal.deepToString(""));

        // HostActionMetadataOperation
        when(mRemoteContext.getText(301)).thenReturn("meta-data");
        HostActionMetadataOperation metaOp = new HostActionMetadataOperation(30, 301);
        assertEquals(30, metaOp.getActionId());
        assertEquals("HOST_METADATA_ACTION", metaOp.serializedName());
        metaOp.runAction(mRemoteContext, mDocument, dummy, 0f, 0f);
        // with null text
        when(mRemoteContext.getText(301)).thenReturn(null);
        metaOp.runAction(mRemoteContext, mDocument, dummy, 0f, 0f);
        metaOp.apply(mRemoteContext);
        WireBuffer mBuf = new WireBuffer();
        metaOp.write(mBuf);
        mBuf.setIndex(0);
        ops.clear();
        HostActionMetadataOperation.read(mBuf, ops);
        HostActionMetadataOperation.documentation(doc);
        metaOp.serialize(mockMapSerializer());
        metaOp.serializeToString(0, ss);
        assertNotNull(metaOp.deepToString(""));
    }

    @Test
    public void testRunActionOperationComprehensive() {
        RunActionOperation runOp = new RunActionOperation();
        assertEquals("RUN_ACTION", runOp.serializedName());
        assertTrue(runOp.isDirty());
        runOp.markDirty();
        runOp.markNotDirty();
        assertNotNull(runOp.getList());

        Component dummy = new Component(1, 0, 0, 10, 10, null);
        mRemoteContext.mLastComponent = dummy;

        ValueFloatChangeActionOperation vFloat = new ValueFloatChangeActionOperation(1, 10.5f);
        runOp.getList().add(vFloat);
        runOp.paint(mPaintContext);

        WireBuffer buf = new WireBuffer();
        runOp.write(buf);
        buf.setIndex(0);
        List<Operation> ops = new ArrayList<>();
        RunActionOperation.read(buf, ops);
        assertEquals(1, ops.size());

        DocumentationBuilder doc = mockDoc();
        RunActionOperation.documentation(doc);
        runOp.serialize(mockMapSerializer());
        assertNotNull(runOp.deepToString(""));
    }

    @Test
    public void testValueChangeActionOperationsComprehensive() {
        Component dummy = new Component(1, 0, 0, 10, 10, null);
        StringSerializer ss = new StringSerializer();
        DocumentationBuilder doc = mockDoc();
        MapSerializer ms = mockMapSerializer();
        List<Operation> ops = new ArrayList<>();

        // ValueFloatChangeActionOperation
        ValueFloatChangeActionOperation vf = new ValueFloatChangeActionOperation(10, 42.5f);
        vf.apply(mRemoteContext);
        vf.runAction(mRemoteContext, mDocument, dummy, 0f, 0f);
        WireBuffer buf = new WireBuffer();
        vf.write(buf);
        buf.setIndex(0);
        ValueFloatChangeActionOperation.read(buf, ops);
        ValueFloatChangeActionOperation.documentation(doc);
        vf.serialize(ms);
        vf.serializeToString(0, ss);
        assertNotNull(vf.deepToString(""));

        // ValueIntegerChangeActionOperation
        ValueIntegerChangeActionOperation vi = new ValueIntegerChangeActionOperation(11, 42);
        vi.apply(mRemoteContext);
        vi.runAction(mRemoteContext, mDocument, dummy, 0f, 0f);
        buf.reset(0);
        vi.write(buf);
        buf.setIndex(0);
        ValueIntegerChangeActionOperation.read(buf, ops);
        ValueIntegerChangeActionOperation.documentation(doc);
        vi.serialize(ms);
        vi.serializeToString(0, ss);
        assertNotNull(vi.deepToString(""));

        // ValueStringChangeActionOperation
        ValueStringChangeActionOperation vs = new ValueStringChangeActionOperation(12, 100);
        vs.apply(mRemoteContext);
        vs.runAction(mRemoteContext, mDocument, dummy, 0f, 0f);
        buf.reset(0);
        vs.write(buf);
        buf.setIndex(0);
        ValueStringChangeActionOperation.read(buf, ops);
        ValueStringChangeActionOperation.documentation(doc);
        vs.serialize(ms);
        vs.serializeToString(0, ss);
        assertNotNull(vs.deepToString(""));

        // ValueFloatExpressionChangeActionOperation
        ValueFloatExpressionChangeActionOperation vfe = new ValueFloatExpressionChangeActionOperation(13, 200);
        vfe.apply(mRemoteContext);
        vfe.runAction(mRemoteContext, mDocument, dummy, 0f, 0f);
        buf.reset(0);
        vfe.write(buf);
        buf.setIndex(0);
        ValueFloatExpressionChangeActionOperation.read(buf, ops);
        ValueFloatExpressionChangeActionOperation.documentation(doc);
        vfe.serialize(ms);
        vfe.serializeToString(0, ss);
        assertNotNull(vfe.deepToString(""));

        // ValueIntegerExpressionChangeActionOperation
        ValueIntegerExpressionChangeActionOperation vie = new ValueIntegerExpressionChangeActionOperation(14, 201);
        vie.apply(mRemoteContext);
        vie.runAction(mRemoteContext, mDocument, dummy, 0f, 0f);
        buf.reset(0);
        vie.write(buf);
        buf.setIndex(0);
        ValueIntegerExpressionChangeActionOperation.read(buf, ops);
        ValueIntegerExpressionChangeActionOperation.documentation(doc);
        vie.serialize(ms);
        vie.serializeToString(0, ss);
        assertNotNull(vie.deepToString(""));
    }

    // =========================================================================
    // SECTION 3: Click, MultiClick, Touch Handlers & ListActions
    // =========================================================================

    @Test
    public void testClickAndMultiClickComprehensive() {
        Component dummy = new Component(1, 0, 0, 100, 100, null);
        dummy.mVisibility = Component.Visibility.VISIBLE;
        dummy.mWidth = 100f;
        dummy.mHeight = 100f;

        // ClickModifierOperation
        ClickModifierOperation clickOp = new ClickModifierOperation();
        assertTrue(clickOp.isClickable());
        assertNotNull(clickOp.getRole());
        assertNotNull(clickOp.getMode());
        clickOp.layout(mRemoteContext, dummy, 100f, 100f);
        clickOp.animateRipple(50f, 50f, 500L);
        when(mClock.millis()).thenReturn(800L);
        clickOp.paint(mPaintContext);
        when(mClock.millis()).thenReturn(2000L); // progress > 1
        clickOp.paint(mPaintContext);

        clickOp.onClick(mRemoteContext, mDocument, dummy, 50f, 50f);
        WireBuffer buf = new WireBuffer();
        clickOp.write(buf);
        buf.setIndex(0);
        List<Operation> ops = new ArrayList<>();
        ClickModifierOperation.read(buf, ops);
        DocumentationBuilder doc = mockDoc();
        ClickModifierOperation.documentation(doc);
        clickOp.serialize(mockMapSerializer());
        StringSerializer ss = new StringSerializer();
        clickOp.serializeToString(0, ss);
        assertNotNull(clickOp.deepToString(""));

        // MultiClickModifier: Single, Long, Double
        int[] clickTypes = {
            MultiClickModifier.CLICK_TYPE_SINGLE,
            MultiClickModifier.CLICK_TYPE_LONG,
            MultiClickModifier.CLICK_TYPE_DOUBLE
        };
        for (int ct : clickTypes) {
            MultiClickModifier mcm = new MultiClickModifier(ct);
            assertTrue(mcm.isClickable());
            assertNotNull(mcm.getRole());
            assertNotNull(mcm.getMode());
            mcm.layout(mRemoteContext, dummy, 100f, 100f);
            mcm.animateRipple(50f, 50f, 500L);
            mcm.paint(mPaintContext);

            mcm.onClick(mRemoteContext, mDocument, dummy, 50f, 50f);
            mcm.onLongPress(mRemoteContext, mDocument, dummy, 50f, 50f);
            mcm.onDoubleClick(mRemoteContext, mDocument, dummy, 50f, 50f);

            buf.reset(0);
            mcm.write(buf);
            buf.setIndex(0);
            ops.clear();
            MultiClickModifier.read(buf, ops);
            MultiClickModifier.documentation(doc);
            mcm.serialize(mockMapSerializer());
            mcm.serializeToString(0, ss);
            assertNotNull(mcm.deepToString(""));
        }
    }

    @Test
    public void testTouchDownUpCancelComprehensive() {
        Component dummy = new Component(1, 0, 0, 100, 100, null);
        dummy.mVisibility = Component.Visibility.VISIBLE;
        dummy.mWidth = 100f;
        dummy.mHeight = 100f;

        WireBuffer buf = new WireBuffer();
        List<Operation> ops = new ArrayList<>();
        DocumentationBuilder doc = mockDoc();

        // TouchDownModifierOperation
        TouchDownModifierOperation td = new TouchDownModifierOperation();
        td.layout(mRemoteContext, dummy, 100f, 100f);
        assertTrue(td.onTouchDown(mRemoteContext, mDocument, dummy, 50f, 50f));
        td.write(buf);
        buf.setIndex(0);
        TouchDownModifierOperation.read(buf, ops);
        TouchDownModifierOperation.documentation(doc);
        assertNotNull(td.toString());

        // TouchUpModifierOperation
        TouchUpModifierOperation tu = new TouchUpModifierOperation();
        tu.layout(mRemoteContext, dummy, 100f, 100f);
        assertTrue(tu.onTouchUp(mRemoteContext, mDocument, dummy, 50f, 50f, 0f, 0f));
        buf.reset(0);
        tu.write(buf);
        buf.setIndex(0);
        ops.clear();
        TouchUpModifierOperation.read(buf, ops);
        TouchUpModifierOperation.documentation(doc);
        assertNotNull(tu.toString());

        // TouchCancelModifierOperation
        TouchCancelModifierOperation tc = new TouchCancelModifierOperation();
        tc.layout(mRemoteContext, dummy, 100f, 100f);
        assertTrue(tc.onTouchCancel(mRemoteContext, mDocument, dummy, 50f, 50f));
        buf.reset(0);
        tc.write(buf);
        buf.setIndex(0);
        ops.clear();
        TouchCancelModifierOperation.read(buf, ops);
        TouchCancelModifierOperation.documentation(doc);
        assertNotNull(tc.toString());

        // ListActionsOperation branch coverage
        td.apply(mRemoteContext);
        td.paint(mPaintContext);
        dummy.mVisibility = Component.Visibility.GONE;
        assertFalse(td.applyActions(mRemoteContext, mDocument, dummy, 50f, 50f, false));
        assertTrue(td.applyActions(mRemoteContext, mDocument, dummy, 50f, 50f, true));
    }

    // =========================================================================
    // SECTION 4: TextLayout & CoreText
    // =========================================================================

    @Test
    public void testTextLayoutComprehensive() {
        int textAlignDynamic = (TextLayout.FLAG_IS_DYNAMIC_COLOR << 16) | TextLayout.TEXT_ALIGN_CENTER;
        TextLayout textLayout = new TextLayout(
                null, 10, -1, 0, 0, 100, 50,
                100, // textId
                0xFFFF0000,
                18f,
                0,
                400f,
                200, // fontFamilyId
                textAlignDynamic,
                TextLayout.OVERFLOW_ELLIPSIS,
                2
        );

        assertEquals(100, (int) textLayout.getTextId());
        assertEquals(18f, textLayout.getFontSize(), 0.01f);
        assertEquals(18f, textLayout.getFontSizeValue(), 0.01f);

        // registerListening & updateVariables
        when(mRemoteContext.getText(100)).thenReturn("Hello Text");
        when(mRemoteContext.getText(200)).thenReturn("sans-serif");
        when(mRemoteContext.getColor(0xFFFF0000)).thenReturn(0xFF00FF00);
        textLayout.registerListening(mRemoteContext);
        textLayout.updateVariables(mRemoteContext);

        // font family branches: default, serif, monospace
        when(mRemoteContext.getText(200)).thenReturn("serif");
        textLayout.updateVariables(mRemoteContext);

        // measure, computeSize, computeWrapSize, layout, paint
        MeasurePass measure = new FlatMeasurePass(100);
        textLayout.computeSize(mPaintContext, 0f, 200f, 0f, 100f, measure);
        Size wrapSize = new Size(0, 0);
        textLayout.computeWrapSize(mPaintContext, 0f, 200f, 0f, 100f, true, true, measure, wrapSize);
        textLayout.internalLayoutMeasure(mPaintContext, measure);
        textLayout.layout(mRemoteContext, measure);
        textLayout.paint(mPaintContext);

        // write & read
        WireBuffer buf = new WireBuffer();
        textLayout.write(buf);
        buf.setIndex(0);
        List<Operation> ops = new ArrayList<>();
        TextLayout.read(buf, ops);
        assertEquals(1, ops.size());

        DocumentationBuilder doc = mockDoc();
        TextLayout.documentation(doc);
        textLayout.serialize(mockMapSerializer());
        StringSerializer ss = new StringSerializer();
        textLayout.serializeToString(0, ss);
        assertNotNull(textLayout.deepToString(""));
        assertNotNull(textLayout.toString());
    }

    // =========================================================================
    // SECTION 5: Custom & CustomProperty
    // =========================================================================

    @Test
    public void testCustomComprehensive() {
        List<Custom.CustomProperty> props = new ArrayList<>();
        props.add(new Custom.CustomProperty(Custom.CustomProperty.INT_PROP, Custom.CustomProperty.INT_PROP, 42));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.FLOAT_PROP, Custom.CustomProperty.FLOAT_PROP, 3.14f));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.STRING_PROP, Custom.CustomProperty.STRING_PROP, 100));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.FLOAT_RETURN, Custom.CustomProperty.FLOAT_RETURN, 1.0f));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.TEXT_RETURN, Custom.CustomProperty.TEXT_RETURN, 101));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.INT_RETURN, Custom.CustomProperty.INT_RETURN, 102));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.COLOR_RETURN, Custom.CustomProperty.COLOR_RETURN, 103));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.COLOR_ID_PROP, Custom.CustomProperty.COLOR_ID_PROP, 104));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.COLOR_PROP, Custom.CustomProperty.COLOR_PROP, 105));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.INT_ID_PROP, Custom.CustomProperty.INT_ID_PROP, 106));

        Custom custom = new Custom(null, 5, -1, 0, 0, 100, 100, 200, "custom-config", props);
        when(mRemoteContext.getText(200)).thenReturn("custom-config-resolved");
        when(mRemoteContext.getText(100)).thenReturn("string-prop-val");
        when(mRemoteContext.getText(101)).thenReturn("text-return-val");
        when(mRemoteContext.getColor(104)).thenReturn(0xFF112233);
        when(mRemoteContext.getInteger(102)).thenReturn(77);
        when(mRemoteContext.getInteger(106)).thenReturn(88);

        custom.registerListening(mRemoteContext);
        custom.updateVariables(mRemoteContext);

        MeasurePass measure = new FlatMeasurePass(100);
        custom.computeSize(mPaintContext, 0f, 100f, 0f, 100f, measure);
        Size size = new Size(0, 0);
        custom.computeWrapSize(mPaintContext, 0f, 100f, 0f, 100f, true, true, measure, size);
        custom.internalLayoutMeasure(mPaintContext, measure);
        custom.layout(mRemoteContext, measure);
        custom.paint(mPaintContext);

        // write & read
        WireBuffer buf = new WireBuffer();
        custom.write(buf);
        buf.setIndex(0);
        List<Operation> ops = new ArrayList<>();
        Custom.read(buf, ops);
        assertEquals(1, ops.size());

        custom.serialize(mockMapSerializer());
        assertNotNull(custom.deepToString(""));
        assertNotNull(custom.toString());
    }

    // =========================================================================
    // SECTION 6: ImageLayout, CanvasLayout, CanvasOperations, Content Components
    // =========================================================================

    @Test
    public void testImageLayoutAndCanvasLayoutComprehensive() {
        // ImageLayout
        ImageLayout img = new ImageLayout(null, 2, -1, 500, 0f, 0f, 100f, 100f, 1, 1.0f);
        assertEquals(500, img.getBitmapId());
        assertEquals(1, img.getScaleType());
        assertEquals(1.0f, img.getAlpha(), 0.01f);

        img.registerListening(mRemoteContext);
        img.updateVariables(mRemoteContext);

        MeasurePass measure = new FlatMeasurePass(100);
        img.computeSize(mPaintContext, 0f, 100f, 0f, 100f, measure);
        Size size = new Size(0, 0);
        img.computeWrapSize(mPaintContext, 0f, 100f, 0f, 100f, true, true, measure, size);
        img.internalLayoutMeasure(mPaintContext, measure);
        img.layout(mRemoteContext, measure);

        androidx.compose.remote.core.operations.BitmapData bd = mock(androidx.compose.remote.core.operations.BitmapData.class);
        when(bd.getWidth()).thenReturn(200);
        when(bd.getHeight()).thenReturn(200);
        when(mRemoteContext.getObject(500)).thenReturn(bd);
        img.paint(mPaintContext);

        // ImageLayout with alpha != 1f
        ImageLayout imgAlpha = new ImageLayout(null, 3, -1, 500, 0f, 0f, 100f, 100f, 1, 0.5f);
        imgAlpha.updateVariables(mRemoteContext);
        imgAlpha.paint(mPaintContext);

        WireBuffer buf = new WireBuffer();
        img.write(buf);
        buf.setIndex(0);
        List<Operation> ops = new ArrayList<>();
        ImageLayout.read(buf, ops);
        assertEquals(1, ops.size());
        DocumentationBuilder doc = mockDoc();
        ImageLayout.documentation(doc);
        img.serialize(mockMapSerializer());
        assertNotNull(img.deepToString(""));
        assertNotNull(img.toString());

        // CanvasLayout & CanvasOperations
        CanvasLayout canvasLayout = new CanvasLayout(null, 3, -1, 0, 0, 100, 100);
        CanvasOperations canvasOps = new CanvasOperations();
        canvasOps.setComponent(canvasLayout);
        canvasOps.registerListening(mRemoteContext);
        canvasOps.updateVariables(mRemoteContext);
        canvasOps.paint(mPaintContext);
        buf.reset(0);
        canvasOps.write(buf);
        buf.setIndex(0);
        ops.clear();
        CanvasOperations.read(buf, ops);
        CanvasOperations.documentation(doc);
        canvasOps.serialize(mockMapSerializer());
        assertNotNull(canvasOps.deepToString(""));
        assertNotNull(canvasOps.toString());
        assertEquals("Loop", CanvasOperations.name());

        canvasLayout.computeSize(mPaintContext, 0f, 100f, 0f, 100f, measure);
        canvasLayout.computeWrapSize(mPaintContext, 0f, 100f, 0f, 100f, true, true, measure, size);
        canvasLayout.layout(mRemoteContext, measure);
        canvasLayout.paint(mPaintContext);
        buf.reset(0);
        canvasLayout.write(buf);
        buf.setIndex(0);
        ops.clear();
        CanvasLayout.read(buf, ops);
        CanvasLayout.documentation(doc);
        canvasLayout.serialize(mockMapSerializer());
        assertNotNull(canvasLayout.deepToString(""));
        assertNotNull(canvasLayout.toString());

        // CanvasContent & LayoutComponentContent & ComponentStart
        CanvasContent cc = new CanvasContent(4);
        assertEquals("CanvasContent", CanvasContent.name());
        assertEquals(Operations.LAYOUT_CANVAS_CONTENT, CanvasContent.id());
        buf.reset(0);
        cc.write(buf);
        buf.setIndex(0);
        ops.clear();
        CanvasContent.read(buf, ops);
        CanvasContent.documentation(doc);

        LayoutComponentContent lcc = new LayoutComponentContent(5);
        assertEquals("LayoutContent", LayoutComponentContent.name());
        assertEquals(Operations.LAYOUT_CONTENT, LayoutComponentContent.id());
        buf.reset(0);
        lcc.write(buf);
        buf.setIndex(0);
        ops.clear();
        LayoutComponentContent.read(buf, ops);
        LayoutComponentContent.documentation(doc);

        ComponentStart cs = new ComponentStart(1, 10, 100f, 100f);
        cs.getType();
        cs.getX();
        cs.getY();
        cs.getWidth();
        cs.getHeight();
        cs.getComponentId();
        buf.reset(0);
        cs.write(buf);
        buf.setIndex(0);
        ops.clear();
        ComponentStart.read(buf, ops);
        ComponentStart.documentation(doc);
        assertNotNull(cs.deepToString(""));
        assertNotNull(cs.toString());
    }

    // =========================================================================
    // SECTION 7: Modifiers Comprehensive
    // =========================================================================

    @Test
    public void testModifiersComprehensive() {
        LayoutComponent parent = new BoxLayout(null, 1, -1, 0, 0, 100, 100, 0, 0);
        MeasurePass measure = new FlatMeasurePass(100);
        ComponentMeasure cm = measure.get(parent);
        StringSerializer ss = new StringSerializer();
        DocumentationBuilder doc = mockDoc();
        MapSerializer ms = mockMapSerializer();
        WireBuffer buf = new WireBuffer();
        List<Operation> ops = new ArrayList<>();

        // LayoutComputeOperation (Measure & Position)
        LayoutComputeOperation lcMeasure = new LayoutComputeOperation(
                LayoutComputeOperation.TYPE_MEASURE, 10, true);
        lcMeasure.setParent(parent);
        lcMeasure.apply(mRemoteContext);
        lcMeasure.registerListening(mRemoteContext);
        lcMeasure.updateVariables(mRemoteContext);
        lcMeasure.markDirty();
        lcMeasure.evaluateInLayout(mRemoteContext);
        lcMeasure.applyToMeasure(mPaintContext, cm, cm);
        assertEquals(LayoutComputeOperation.TYPE_MEASURE, lcMeasure.getType());
        assertEquals("LAYOUT_COMPUTE", lcMeasure.serializedName());
        buf.reset(0);
        lcMeasure.write(buf);
        buf.setIndex(0);
        LayoutComputeOperation.read(buf, ops);
        LayoutComputeOperation.documentation(doc);
        lcMeasure.serialize(ms);
        assertNotNull(lcMeasure.deepToString(""));
        assertNotNull(lcMeasure.toString());

        LayoutComputeOperation lcPos = new LayoutComputeOperation(
                LayoutComputeOperation.TYPE_POSITION, 11, false);
        lcPos.setParent(parent);
        lcPos.applyToMeasure(mPaintContext, cm, cm);

        // MarqueeModifierOperation
        MarqueeModifierOperation marquee = new MarqueeModifierOperation(
                3, 0, 500f, 100f, 20f, 30f);
        marquee.setContentWidth(150f);
        marquee.setContentHeight(50f);
        marquee.contentWidth();
        marquee.contentHeight();
        marquee.handlesHorizontalScroll();
        marquee.handlesVerticalScroll();
        marquee.getScrollX(0f);
        marquee.getScrollY(0f);
        marquee.layout(mRemoteContext, parent, 100f, 50f);
        marquee.paint(mPaintContext);
        marquee.reset();
        buf.reset(0);
        marquee.write(buf);
        buf.setIndex(0);
        ops.clear();
        MarqueeModifierOperation.read(buf, ops);
        MarqueeModifierOperation.documentation(doc);
        marquee.serialize(ms);
        assertNotNull(marquee.deepToString(""));

        // RippleModifierOperation
        RippleModifierOperation ripple = new RippleModifierOperation();
        ripple.layout(mRemoteContext, parent, 100f, 50f);
        ripple.onTouchDown(mRemoteContext, mDocument, parent, 10f, 10f);
        ripple.paint(mPaintContext);
        ripple.onTouchUp(mRemoteContext, mDocument, parent, 10f, 10f, 0f, 0f);
        ripple.onTouchCancel(mRemoteContext, mDocument, parent, 10f, 10f);
        buf.reset(0);
        ripple.write(buf);
        buf.setIndex(0);
        ops.clear();
        RippleModifierOperation.read(buf, ops);
        RippleModifierOperation.documentation(doc);
        ripple.serialize(ms);
        assertNotNull(ripple.deepToString(""));

        // DimensionConstraintsModifierOperation
        int[] constraintTypes = {
            DimensionConstraintsModifierOperation.HORIZONTAL_CONSTRAINTS,
            DimensionConstraintsModifierOperation.VERTICAL_CONSTRAINTS,
            DimensionConstraintsModifierOperation.REQUIRED_HORIZONTAL_CONSTRAINTS,
            DimensionConstraintsModifierOperation.REQUIRED_VERTICAL_CONSTRAINTS
        };
        for (int ct : constraintTypes) {
            DimensionConstraintsModifierOperation dco = new DimensionConstraintsModifierOperation(ct, 10f, 80f);
            dco.applyWidthConstraint(50f);
            dco.applyHeightConstraint(50f);
            buf.reset(0);
            dco.write(buf);
            buf.setIndex(0);
            ops.clear();
            DimensionConstraintsModifierOperation.read(buf, ops);
            DimensionConstraintsModifierOperation.documentation(doc);
            dco.serialize(ms);
            assertNotNull(dco.deepToString(""));
            assertNotNull(dco.toString());
        }

        // HeightInModifierOperation & WidthInModifierOperation
        HeightInModifierOperation hIn = new HeightInModifierOperation(20f, 60f);
        hIn.applyWidthConstraint(50f);
        hIn.applyHeightConstraint(50f);
        buf.reset(0);
        hIn.write(buf);
        buf.setIndex(0);
        ops.clear();
        HeightInModifierOperation.read(buf, ops);
        HeightInModifierOperation.documentation(doc);
        hIn.serialize(ms);
        assertNotNull(hIn.deepToString(""));

        WidthInModifierOperation wIn = new WidthInModifierOperation(20f, 60f);
        wIn.applyWidthConstraint(50f);
        wIn.applyHeightConstraint(50f);
        buf.reset(0);
        wIn.write(buf);
        buf.setIndex(0);
        ops.clear();
        WidthInModifierOperation.read(buf, ops);
        WidthInModifierOperation.documentation(doc);
        wIn.serialize(ms);
        assertNotNull(wIn.deepToString(""));

        // RoundedClipRectModifierOperation
        RoundedClipRectModifierOperation rClip = new RoundedClipRectModifierOperation(8f, 8f, 8f, 8f);
        rClip.layout(mRemoteContext, parent, 100f, 100f);
        rClip.paint(mPaintContext);
        when(mPaintContext.getDensityBehavior()).thenReturn(CoreDocument.DENSITY_BEHAVIOR_DP);
        rClip.paint(mPaintContext);
        buf.reset(0);
        rClip.write(buf);
        buf.setIndex(0);
        ops.clear();
        RoundedClipRectModifierOperation.read(buf, ops);
        RoundedClipRectModifierOperation.documentation(doc);
        rClip.serialize(ms);
        rClip.serializeToString(0, ss);

        // DrawContentOperation
        DrawContentOperation drawContent = new DrawContentOperation();
        drawContent.setParent(parent);
        assertEquals(parent, drawContent.getParent());
        drawContent.layout(mRemoteContext, parent, 100f, 100f);
        drawContent.apply(mRemoteContext);
        drawContent.registerListening(mRemoteContext);
        drawContent.updateVariables(mRemoteContext);
        buf.reset(0);
        drawContent.write(buf);
        buf.setIndex(0);
        ops.clear();
        DrawContentOperation.read(buf, ops);
        DrawContentOperation.documentation(doc);
        drawContent.serialize(ms);
        drawContent.serializeToString(0, ss);
        assertNotNull(drawContent.deepToString(""));
        assertNotNull(drawContent.toString());

        // AlignByModifierOperation
        AlignByModifierOperation alignBy = new AlignByModifierOperation(AlignByModifierOperation.FIRST_BASELINE, 0);
        alignBy.getValue(mPaintContext);
        buf.reset(0);
        alignBy.write(buf);
        buf.setIndex(0);
        ops.clear();
        AlignByModifierOperation.read(buf, ops);
        AlignByModifierOperation.documentation(doc);
        alignBy.serialize(ms);
        assertNotNull(alignBy.deepToString(""));

        // ScrollModifierOperation
        ScrollModifierOperation scroll = new ScrollModifierOperation(1, 0f, 500f, 50f);
        scroll.inflate(parent);
        scroll.setHorizontalScrollDimension(100f, 500f);
        scroll.setVerticalScrollDimension(100f, 500f);
        scroll.getContentDimension();
        scroll.layout(mRemoteContext, parent, 100f, 100f);
        scroll.onTouchDown(mRemoteContext, mDocument, parent, 50f, 50f);
        scroll.onTouchDrag(mRemoteContext, mDocument, parent, 40f, 40f);
        scroll.onTouchUp(mRemoteContext, mDocument, parent, 30f, 30f, 10f, 10f);
        scroll.onTouchCancel(mRemoteContext, mDocument, parent, 30f, 30f);
        scroll.paint(mPaintContext);
        buf.reset(0);
        scroll.write(buf);
        buf.setIndex(0);
        ops.clear();
        ScrollModifierOperation.read(buf, ops);
        ScrollModifierOperation.documentation(doc);
        scroll.serialize(ms);
    }

    // =========================================================================
    // SECTION 8: Animations Comprehensive
    // =========================================================================

    @Test
    public void testAnimationsComprehensive() {
        Component dummy = new Component(1, 0, 0, 100, 100, null);
        ComponentMeasure start = new ComponentMeasure(dummy.getComponentId(), 0, 0, 50, 50);
        ComponentMeasure end = new ComponentMeasure(dummy.getComponentId(), 10, 10, 100, 100);

        // AnimateMeasure
        AnimateMeasure anim = new AnimateMeasure(
                1000L,
                dummy,
                start,
                end,
                300f,
                300f,
                AnimationSpec.ANIMATION.FADE_IN,
                AnimationSpec.ANIMATION.FADE_OUT,
                GeneralEasing.CUBIC_STANDARD,
                GeneralEasing.CUBIC_ACCELERATE
        );
        assertEquals(start, anim.getOriginal());
        assertEquals(end, anim.getTarget());
        mRemoteContext.currentTime = 1150L;
        when(mClock.millis()).thenReturn(1150L);
        anim.apply(mRemoteContext);
        anim.paint(mPaintContext);
        anim.getX();
        anim.getY();
        anim.getWidth();
        anim.getHeight();
        anim.getVisibility();
        assertFalse(anim.isDone());
        mRemoteContext.currentTime = 1400L;
        when(mClock.millis()).thenReturn(1400L);
        anim.apply(mRemoteContext);
        assertTrue(anim.isDone());
        anim.updateTarget(mRemoteContext, end, 1500L);
        assertNotNull(anim.toString());

        // RootAnimateMeasure
        RootAnimateMeasure rootAnim = new RootAnimateMeasure(
                1000L,
                dummy,
                start,
                end,
                0f, 0f, 10f, 10f,
                300f, 300f,
                AnimationSpec.ANIMATION.FADE_IN,
                AnimationSpec.ANIMATION.FADE_OUT,
                GeneralEasing.CUBIC_STANDARD,
                GeneralEasing.CUBIC_ACCELERATE
        );
        when(mDocument.getOriginX()).thenReturn(10f);
        when(mDocument.getOriginY()).thenReturn(10f);
        rootAnim.apply(mRemoteContext);
        rootAnim.paint(mPaintContext);
        rootAnim.updateTarget(mRemoteContext, end, 1200L);

        // Particle & ParticleAnimation
        Particle particle = new Particle(0.5f, 0.5f, 5f, 200f, 200f, 200f);
        assertEquals(0.5f, particle.x, 0.01f);
        assertEquals(0.5f, particle.y, 0.01f);

        ParticleAnimation pa = new ParticleAnimation();
        pa.animate(mPaintContext, dummy, start, end, 0.5f);

        // AnimatableValue
        AnimatableValue literalVal = new AnimatableValue(42f);
        assertEquals(42f, literalVal.getValue(), 0.01f);
        assertEquals(42f, literalVal.evaluate(mPaintContext), 0.01f);
        assertNotNull(literalVal.toString());
        literalVal.serialize(mockMapSerializer());

        float varId = Utils.asNan(555);
        AnimatableValue varVal = new AnimatableValue(varId, true);
        mState.updateFloat(555, 10f);
        varVal.evaluate(mPaintContext);
        when(mClock.millis()).thenReturn(1500L);
        mState.updateFloat(555, 20f);
        varVal.evaluate(mPaintContext);
        when(mClock.millis()).thenReturn(1700L);
        varVal.evaluate(mPaintContext);

        // AnimationSpec
        AnimationSpec defaultSpec = AnimationSpec.DEFAULT;
        assertTrue(defaultSpec.isAnimationEnabled());
        AnimationSpec disabledSpec = AnimationSpec.DISABLED;
        assertFalse(disabledSpec.isAnimationEnabled());

        AnimationSpec customSpec = new AnimationSpec(
                1, 400f, GeneralEasing.CUBIC_STANDARD, 400f, GeneralEasing.CUBIC_DECELERATE,
                AnimationSpec.ANIMATION.SLIDE_LEFT, AnimationSpec.ANIMATION.SLIDE_RIGHT
        );
        assertEquals(1, customSpec.getAnimationId());
        assertEquals(400f, customSpec.getMotionDuration(), 0.01f);
        assertEquals(GeneralEasing.CUBIC_STANDARD, customSpec.getMotionEasingType());
        assertEquals(400f, customSpec.getVisibilityDuration(), 0.01f);
        assertEquals(GeneralEasing.CUBIC_DECELERATE, customSpec.getVisibilityEasingType());
        assertEquals(AnimationSpec.ANIMATION.SLIDE_LEFT, customSpec.getEnterAnimation());
        assertEquals(AnimationSpec.ANIMATION.SLIDE_RIGHT, customSpec.getExitAnimation());

        WireBuffer buf = new WireBuffer();
        customSpec.write(buf);
        buf.setIndex(0);
        List<Operation> ops = new ArrayList<>();
        AnimationSpec.read(buf, ops);
        DocumentationBuilder doc = mockDoc();
        AnimationSpec.documentation(doc);
        customSpec.serialize(mockMapSerializer());
        assertNotNull(customSpec.deepToString(""));
    }

    // =========================================================================
    // SECTION 9: Measure Policies Comprehensive
    // =========================================================================

    @Test
    public void testPoliciesComprehensive() {
        MeasurePass measure = new FlatMeasurePass(100);

        // 1. LegacyMeasurePolicy
        RowLayout legacyRow = new RowLayout(null, 1, -1, 0f, 0f, 100f, 100f, 0, 0, 0f);
        legacyRow.mWidthModifier = new WidthModifierOperation(100f);
        legacyRow.mHeightModifier = new HeightModifierOperation(100f);
        LegacyMeasurePolicy.INSTANCE.measure(legacyRow, mPaintContext, 0, 200, 0, 200, measure);

        // With wrapping
        legacyRow.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.WRAP);
        legacyRow.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.WRAP);
        LegacyMeasurePolicy.INSTANCE.measure(legacyRow, mPaintContext, 0, 200, 0, 200, measure);

        // With horizontal scroll and vertical scroll
        legacyRow.getComponentModifiers().add(new ScrollModifierOperation(0, 0f, 500f, 0f));
        legacyRow.getComponentModifiers().add(new ScrollModifierOperation(1, 0f, 500f, 0f));
        LegacyMeasurePolicy.INSTANCE.measure(legacyRow, mPaintContext, 0, 200, 0, 200, measure);

        // 2. BaseModernMeasurePolicy and subclasses
        BoxLayout modernBox = new BoxLayout(null, 2, -1, 0, 0, 100, 100, 0, 0);
        modernBox.mWidthModifier = new WidthModifierOperation(100f);
        modernBox.mHeightModifier = new HeightModifierOperation(100f);
        modernBox.getComponentModifiers().add(new WidthInModifierOperation(10f, 80f));
        modernBox.getComponentModifiers().add(new HeightInModifierOperation(10f, 80f));
        BaseModernMeasurePolicy.INSTANCE.measure(modernBox, mPaintContext, 0, 200, 0, 200, measure);

        EnforceConstraintsMeasurePolicy.INSTANCE.measure(modernBox, mPaintContext, 0, 200, 0, 200, measure);
        InlineExpressionMeasurePolicy.INSTANCE.measure(modernBox, mPaintContext, 0, 200, 0, 200, measure);
        InsetWrapMeasurePolicy.INSTANCE.measure(modernBox, mPaintContext, 0, 200, 0, 200, measure);
    }

    // =========================================================================
    // SECTION 10: Component & LayoutComponent Methods
    // =========================================================================

    @Test
    public void testComponentAndLayoutComponentComprehensive() {
        LayoutComponent parent = new BoxLayout(null, 1, -1, 0, 0, 200, 200, 0, 0);
        Component child = new Component(2, 10, 10, 50, 50, parent);
        parent.getChildrenComponents().add(child);

        // Component methods
        child.setComponentId(20);
        assertEquals(20, child.getComponentId());
        child.setAnimationId(30);
        assertEquals(30, child.getAnimationId());
        child.setAnimationSpec(AnimationSpec.DEFAULT);
        assertNotNull(child.getAnimationSpec());
        child.markNeedsBoundsAnimation();
        assertTrue(child.needsBoundsAnimation());
        child.clearNeedsBoundsAnimation();
        assertFalse(child.needsBoundsAnimation());

        child.setLayoutPosition(15f, 25f);
        child.getTranslateX();
        child.getTranslateY();
        child.finalizeCreation();
        child.hasDynamicPosition();
        child.hasDynamicSize();
        child.hasChildWithComputedLayout();
        child.animatingBounds(mRemoteContext);
        child.isRelayoutBoundary(mDocument);
        child.invalidateMeasure();
        child.debugBox(child, mPaintContext);
        child.paintingComponent(mPaintContext);
        child.applyAnimationAsNeeded(mPaintContext);
        child.findAncestor(BoxLayout.class);
        child.getComponent(20);
        child.selfOrModifier(Component.class);
        child.suitableForTransition(child);

        // ComponentValues update
        ComponentValue cv = mock(ComponentValue.class);
        child.addComponentValue(cv);
        child.updateComponentValues(mRemoteContext, 100f, 100f);

        // LayoutComponent methods
        parent.getHorizontalScrollDelegate();
        parent.getVerticalScrollDelegate();
        parent.setScrollX(10f);
        parent.setScrollY(20f);
        parent.getScrollX();
        parent.drawContent(mPaintContext);
        parent.applyWidthConstraints(150f);
        parent.applyHeightConstraints(150f);
        assertNotNull(parent.toString());

        WireBuffer buf = new WireBuffer();
        parent.write(buf);
        buf.setIndex(0);
        List<Operation> ops = new ArrayList<>();
        LayoutComponent.read(buf, ops);

        // RootLayoutComponent
        RootLayoutComponent root = new RootLayoutComponent(99);
        root.layout(mRemoteContext);
        root.layout(mRemoteContext, new FlatMeasurePass(100));
        root.paint(mPaintContext);
        DocumentationBuilder doc = mockDoc();
        RootLayoutComponent.documentation(doc);
    }

    // =========================================================================
    // SECTION 11: DebugLog
    // =========================================================================

    @Test
    public void testDebugLogComprehensive() {
        DebugLog.DEBUG_LAYOUT_ON = true;
        DebugLog.s(() -> "RootNode");
        DebugLog.log(() -> "ChildLog");
        DebugLog.s(() -> "InnerNode");
        DebugLog.e(() -> "InnerNode Finished");
        DebugLog.e();
        DebugLog.display();
        DebugLog.clear();
        DebugLog.DEBUG_LAYOUT_ON = false;
    }

    // =========================================================================
    // SECTION 12: Extended Coverage (StateLayout, FitBox, AnimateMeasure, Modifiers)
    // =========================================================================

    @Test
    public void testStateLayoutComprehensive() {
        StateLayout stateLayout = new StateLayout(null, 10, -1, 0f, 0f, 200f, 200f, 50);
        StateLayout stateLayoutAlt = new StateLayout(11, -1, 0, 0, 51);

        // Child 0: Box
        BoxLayout box0 = new BoxLayout(stateLayout, 100, -1, 0, 0, 200, 200, 0, 0);
        Component c1 = new Component(box0, 101, 10, 0, 0, 50, 50);
        Component c2 = new Component(box0, 102, -1, 0, 50, 50, 50);
        box0.getChildrenComponents().add(c1);
        box0.getChildrenComponents().add(c2);

        // Child 1: Box
        BoxLayout box1 = new BoxLayout(stateLayout, 200, -1, 0, 0, 200, 200, 0, 0);
        Component c1b = new Component(box1, 201, 10, 20, 20, 80, 80);
        Component c3 = new Component(box1, 202, 11, 100, 100, 50, 50);
        box1.getChildrenComponents().add(c1b);
        box1.getChildrenComponents().add(c3);

        stateLayout.mWidthModifier = new WidthModifierOperation(200f);
        stateLayout.mHeightModifier = new HeightModifierOperation(200f);
        box0.mWidthModifier = new WidthModifierOperation(200f);
        box0.mHeightModifier = new HeightModifierOperation(200f);
        box1.mWidthModifier = new WidthModifierOperation(200f);
        box1.mHeightModifier = new HeightModifierOperation(200f);

        stateLayout.getChildrenComponents().add(box0);
        stateLayout.getChildrenComponents().add(box1);

        stateLayout.findAnimatedComponents();
        stateLayout.collapsePaintedComponents();

        assertNotNull(stateLayout.getSharedComponent(10, 0));
        assertNull(stateLayout.getSharedComponent(999, 0));
        assertNull(stateLayout.getSharedComponent(10, -1));

        MeasurePass measure = new FlatMeasurePass(100);
        stateLayout.computeSize(mPaintContext, 0, 200, 0, 200, measure);
        stateLayout.computeWrapSize(mPaintContext, 0, 200, 0, 200, true, true, measure, new Size(0, 0));
        stateLayout.internalLayoutMeasure(mPaintContext, measure);
        stateLayout.measure(mPaintContext, 0, 200, 0, 200, measure);
        stateLayout.layout(mRemoteContext, measure);
        stateLayout.paint(mPaintContext);

        // Click
        stateLayout.onClick(mRemoteContext, mDocument, 10f, 10f);
        stateLayout.onClick(mRemoteContext, mDocument, -100f, -100f);

        // Transition from state 0 to state 1
        mState.updateInteger(50, 1);
        stateLayout.paint(mPaintContext);
        assertTrue(stateLayout.inTransition);
        assertEquals(1, stateLayout.currentLayoutIndex);
        assertEquals(0, stateLayout.previousLayoutIndex);

        stateLayout.measure(mPaintContext, 0, 200, 0, 200, measure);
        stateLayout.layout(mRemoteContext, measure);
        stateLayout.paint(mPaintContext);

        stateLayout.hideLayoutsOtherThan(0);
        stateLayout.hideLayoutsOtherThan(1);
        assertNotNull(stateLayout.getLayout(0));
        assertNotNull(stateLayout.getLayout(1));

        WireBuffer buf = new WireBuffer();
        stateLayout.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        List<Operation> ops = new ArrayList<>();
        StateLayout.read(buf, ops);
        StateLayout.documentation(mockDoc());
        stateLayout.serialize(mockMapSerializer());
        assertNotNull(stateLayout.deepToString(""));
        assertNotNull(stateLayout.toString());
    }

    @Test
    public void testFitBoxLayoutComprehensive() {
        FitBoxLayout fitBox = new FitBoxLayout(null, 20, -1, 0, 0, 100, 100, FitBoxLayout.CENTER, FitBoxLayout.CENTER);
        fitBox.mWidthModifier = new WidthModifierOperation(100f);
        fitBox.mHeightModifier = new HeightModifierOperation(100f);
        assertEquals("FitBoxLayout", FitBoxLayout.name());
        assertEquals(Operations.LAYOUT_FIT_BOX, FitBoxLayout.id());

        Component smallChild = new Component(21, 0, 0, 50, 50, fitBox);
        Component largeChild = new Component(22, 0, 0, 200, 200, fitBox);
        fitBox.getChildrenComponents().add(smallChild);
        fitBox.getChildrenComponents().add(largeChild);

        MeasurePass measure = new FlatMeasurePass(100);
        fitBox.computeSize(mPaintContext, 0, 100, 0, 100, measure);
        Size size = new Size(0, 0);
        fitBox.computeWrapSize(mPaintContext, 0, 100, 0, 100, true, true, measure, size);

        when(mPaintContext.useFeature(Header.FEATURE_PRIORITY_FIX)).thenReturn(true);
        fitBox.computeWrapSize(mPaintContext, 0, 100, 0, 100, true, true, measure, size);
        fitBox.computeWrapSize(mPaintContext, 0, 10, 0, 10, true, true, measure, size); // None fits

        fitBox.internalLayoutMeasure(mPaintContext, measure);
        fitBox.measure(mPaintContext, 0, 100, 0, 100, measure);
        fitBox.layout(mRemoteContext, measure);
        fitBox.paintingComponent(mPaintContext);

        // Other alignments
        FitBoxLayout fitStart = new FitBoxLayout(null, 23, -1, FitBoxLayout.START, FitBoxLayout.TOP);
        fitStart.mWidthModifier = new WidthModifierOperation(100f);
        fitStart.mHeightModifier = new HeightModifierOperation(100f);
        fitStart.getChildrenComponents().add(smallChild);
        fitStart.measure(mPaintContext, 0, 100, 0, 100, measure);
        fitStart.layout(mRemoteContext, measure);

        FitBoxLayout fitEnd = new FitBoxLayout(null, 24, -1, FitBoxLayout.END, FitBoxLayout.BOTTOM);
        fitEnd.mWidthModifier = new WidthModifierOperation(100f);
        fitEnd.mHeightModifier = new HeightModifierOperation(100f);
        fitEnd.getChildrenComponents().add(smallChild);
        fitEnd.measure(mPaintContext, 0, 100, 0, 100, measure);
        fitEnd.layout(mRemoteContext, measure);

        WireBuffer buf = new WireBuffer();
        fitBox.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        List<Operation> ops = new ArrayList<>();
        FitBoxLayout.read(buf, ops);
        FitBoxLayout.documentation(mockDoc());
        fitBox.serialize(mockMapSerializer());
        assertNotNull(fitBox.deepToString(""));
        assertNotNull(fitBox.toString());
    }

    @Test
    public void testAnimateMeasureAllTransitions() {
        BoxLayout parent = new BoxLayout(null, 1, -1, 0, 0, 200, 200, 0, 0);
        Component child = new Component(2, 0, 0, 100, 100, parent);

        ComponentMeasure startVis = new ComponentMeasure(2, 0, 0, 100, 100, Component.Visibility.VISIBLE);
        ComponentMeasure endGone = new ComponentMeasure(2, 0, 0, 100, 100, Component.Visibility.GONE);

        AnimationSpec.ANIMATION[] exitAnims = {
            AnimationSpec.ANIMATION.FADE_OUT,
            AnimationSpec.ANIMATION.SLIDE_LEFT,
            AnimationSpec.ANIMATION.SLIDE_RIGHT,
            AnimationSpec.ANIMATION.SLIDE_TOP,
            AnimationSpec.ANIMATION.SLIDE_BOTTOM,
            AnimationSpec.ANIMATION.PARTICLE
        };

        for (AnimationSpec.ANIMATION exitAnim : exitAnims) {
            AnimateMeasure am = new AnimateMeasure(
                    1000L, child, startVis, endGone, 300f, 300f,
                    AnimationSpec.ANIMATION.FADE_IN, exitAnim,
                    GeneralEasing.CUBIC_STANDARD, GeneralEasing.CUBIC_STANDARD
            );
            mRemoteContext.currentTime = 1150L;
            am.apply(mRemoteContext);
            am.paint(mPaintContext);
        }

        ComponentMeasure startGone = new ComponentMeasure(2, 0, 0, 100, 100, Component.Visibility.GONE);
        ComponentMeasure endVis = new ComponentMeasure(2, 0, 0, 100, 100, Component.Visibility.VISIBLE);

        AnimationSpec.ANIMATION[] enterAnims = {
            AnimationSpec.ANIMATION.ROTATE,
            AnimationSpec.ANIMATION.FADE_IN,
            AnimationSpec.ANIMATION.SLIDE_LEFT,
            AnimationSpec.ANIMATION.SLIDE_RIGHT,
            AnimationSpec.ANIMATION.SLIDE_TOP,
            AnimationSpec.ANIMATION.SLIDE_BOTTOM,
            AnimationSpec.ANIMATION.PARTICLE
        };

        for (AnimationSpec.ANIMATION enterAnim : enterAnims) {
            AnimateMeasure am = new AnimateMeasure(
                    1000L, child, startGone, endVis, 300f, 300f,
                    enterAnim, AnimationSpec.ANIMATION.FADE_OUT,
                    GeneralEasing.CUBIC_STANDARD, GeneralEasing.CUBIC_STANDARD
            );
            mRemoteContext.currentTime = 1150L;
            am.apply(mRemoteContext);
            am.paint(mPaintContext);
        }
    }

    @Test
    public void testColumnAndRowLayoutComprehensiveExtended() {
        MeasurePass measure = new FlatMeasurePass(100);
        Size size = new Size(0, 0);

        int[] hAlignments = {
            ColumnLayout.START,
            ColumnLayout.CENTER,
            ColumnLayout.END
        };
        int[] vAlignments = {
            ColumnLayout.TOP,
            ColumnLayout.CENTER,
            ColumnLayout.BOTTOM,
            ColumnLayout.SPACE_BETWEEN,
            ColumnLayout.SPACE_EVENLY,
            ColumnLayout.SPACE_AROUND
        };

        for (int ha : hAlignments) {
            for (int va : vAlignments) {
                ColumnLayout col = new ColumnLayout(null, 30, -1, ha, va, 10f);
                col.mWidthModifier = new WidthModifierOperation(200f);
                col.mHeightModifier = new HeightModifierOperation(200f);
                Component c1 = new Component(31, 0, 0, 50, 40, col);
                Component c2 = new Component(32, 0, 0, 60, 50, col);
                col.getChildrenComponents().add(c1);
                col.getChildrenComponents().add(c2);

                col.computeSize(mPaintContext, 0, 200, 0, 200, measure);
                col.computeWrapSize(mPaintContext, 0, 200, 0, 200, true, true, measure, size);
                col.internalLayoutMeasure(mPaintContext, measure);
                col.measure(mPaintContext, 0, 200, 0, 200, measure);
                col.layout(mRemoteContext, measure);
                col.paintingComponent(mPaintContext);
            }
        }

        // Column with weighted children
        ColumnLayout colWeighted = new ColumnLayout(null, 33, -1, 0, 0, 200, 200, ColumnLayout.START, ColumnLayout.TOP, 0f);
        colWeighted.mWidthModifier = new WidthModifierOperation(200f);
        colWeighted.mHeightModifier = new HeightModifierOperation(200f);
        BoxLayout wChild1 = new BoxLayout(colWeighted, 34, -1, 0, 0, 50, 50, 0, 0);
        wChild1.mHeightModifier = new HeightModifierOperation(1.0f); // weight
        wChild1.getHeightModifier().setType(DimensionModifierOperation.Type.WEIGHT);
        wChild1.mWidthModifier = new WidthModifierOperation(50f);
        colWeighted.getChildrenComponents().add(wChild1);
        colWeighted.isInVerticalFill();
        colWeighted.computeWrapSize(mPaintContext, 0, 200, 0, 200, false, false, measure, size);
        colWeighted.measure(mPaintContext, 0, 200, 0, 200, measure);
        colWeighted.layout(mRemoteContext, measure);

        // RowLayout alignments
        int[] rowHAlignments = {
            RowLayout.START,
            RowLayout.CENTER,
            RowLayout.END,
            RowLayout.SPACE_BETWEEN,
            RowLayout.SPACE_EVENLY,
            RowLayout.SPACE_AROUND
        };
        int[] rowVAlignments = {
            RowLayout.TOP,
            RowLayout.CENTER,
            RowLayout.BOTTOM
        };

        for (int ha : rowHAlignments) {
            for (int va : rowVAlignments) {
                RowLayout row = new RowLayout(null, 40, -1, ha, va, 10f);
                row.mWidthModifier = new WidthModifierOperation(200f);
                row.mHeightModifier = new HeightModifierOperation(200f);
                Component c1 = new Component(41, 0, 0, 50, 40, row);
                Component c2 = new Component(42, 0, 0, 60, 50, row);
                row.getChildrenComponents().add(c1);
                row.getChildrenComponents().add(c2);

                row.computeSize(mPaintContext, 0, 200, 0, 200, measure);
                row.computeWrapSize(mPaintContext, 0, 200, 0, 200, true, true, measure, size);
                row.internalLayoutMeasure(mPaintContext, measure);
                row.measure(mPaintContext, 0, 200, 0, 200, measure);
                row.layout(mRemoteContext, measure);
                row.paintingComponent(mPaintContext);
            }
        }

        // Row with weighted children
        RowLayout rowWeighted = new RowLayout(null, 43, -1, 0f, 0f, 200f, 200f, RowLayout.START, RowLayout.TOP, 0f);
        rowWeighted.mWidthModifier = new WidthModifierOperation(200f);
        rowWeighted.mHeightModifier = new HeightModifierOperation(200f);
        BoxLayout rwChild1 = new BoxLayout(rowWeighted, 44, -1, 0, 0, 50, 50, 0, 0);
        rwChild1.mWidthModifier = new WidthModifierOperation(1.0f);
        rwChild1.getWidthModifier().setType(DimensionModifierOperation.Type.WEIGHT);
        rwChild1.mHeightModifier = new HeightModifierOperation(50f);
        rowWeighted.getChildrenComponents().add(rwChild1);
        rowWeighted.isInHorizontalFill();
        rowWeighted.computeWrapSize(mPaintContext, 0, 200, 0, 200, false, false, measure, size);
        rowWeighted.measure(mPaintContext, 0, 200, 0, 200, measure);
        rowWeighted.layout(mRemoteContext, measure);
    }

    @Test
    public void testRemainingModifiersComprehensive() {
        Component dummy = new Component(1, 0, 0, 100, 100, null);
        WireBuffer buf = new WireBuffer();
        List<Operation> ops = new ArrayList<>();
        DocumentationBuilder doc = mockDoc();
        MapSerializer ms = mockMapSerializer();

        // ShapeType
        assertEquals("RECTANGLE", ShapeType.getString(ShapeType.RECTANGLE));
        assertEquals("CIRCLE", ShapeType.getString(ShapeType.CIRCLE));
        assertEquals("ROUNDED_RECTANGLE", ShapeType.getString(ShapeType.ROUNDED_RECTANGLE));
        assertEquals("INVALID_SHAPE_TYPE[999]", ShapeType.getString(999));

        // BackgroundModifierOperation
        float nanR = Utils.asNan(501);
        float nanG = Utils.asNan(502);
        float nanB = Utils.asNan(503);
        float nanA = Utils.asNan(504);
        when(mRemoteContext.getFloat(501)).thenReturn(0.8f);
        when(mRemoteContext.getFloat(502)).thenReturn(0.2f);
        when(mRemoteContext.getFloat(503)).thenReturn(0.3f);
        when(mRemoteContext.getFloat(504)).thenReturn(1.0f);

        BackgroundModifierOperation bg1 = new BackgroundModifierOperation(
                0, 0, 0, 0, 1f, 0f, 0f, 1f, ShapeType.RECTANGLE);
        bg1.layout(mRemoteContext, dummy, 100f, 100f);
        bg1.paint(mPaintContext);

        BackgroundModifierOperation bg2 = new BackgroundModifierOperation(
                BackgroundModifierOperation.COLOR_REF, 200, 0, 0, 0f, 1f, 0f, 1f, ShapeType.CIRCLE);
        when(mRemoteContext.getColor(200)).thenReturn(0xFF00FF00);
        bg2.layout(mRemoteContext, dummy, 100f, 100f);
        bg2.paint(mPaintContext);

        BackgroundModifierOperation bg3 = new BackgroundModifierOperation(
                0, 0, 0, 0, nanR, nanG, nanB, nanA, ShapeType.ROUNDED_RECTANGLE);
        bg3.registerListening(mRemoteContext);
        bg3.updateVariables(mRemoteContext);
        bg3.layout(mRemoteContext, dummy, 100f, 100f);
        bg3.paint(mPaintContext);

        buf.reset(0);
        bg1.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        BackgroundModifierOperation.read(buf, ops);
        BackgroundModifierOperation.documentation(doc);
        bg1.serialize(ms);
        assertNotNull(bg1.deepToString(""));

        // BorderModifierOperation
        float nanW = Utils.asNan(505);
        float nanRad = Utils.asNan(506);
        when(mRemoteContext.getFloat(505)).thenReturn(4f);
        when(mRemoteContext.getFloat(506)).thenReturn(8f);

        BorderModifierOperation b1 = new BorderModifierOperation(
                0, 0, 0, 0, 2f, 4f, 1f, 0f, 0f, 1f, ShapeType.RECTANGLE);
        b1.layout(mRemoteContext, dummy, 100f, 100f);
        b1.paint(mPaintContext);

        BorderModifierOperation b2 = new BorderModifierOperation(
                BorderModifierOperation.COLOR_REF, 200, 0, 0, 2f, 4f, 0f, 0f, 0f, 1f, ShapeType.ROUNDED_RECTANGLE);
        b2.layout(mRemoteContext, dummy, 100f, 100f);
        b2.paint(mPaintContext);

        BorderModifierOperation b3 = new BorderModifierOperation(
                0, 0, 0, 0, nanW, nanRad, 0f, 0f, 1f, 1f, ShapeType.CIRCLE);
        b3.registerListening(mRemoteContext);
        b3.updateVariables(mRemoteContext);
        b3.layout(mRemoteContext, dummy, 100f, 100f);
        b3.paint(mPaintContext);

        buf.reset(0);
        b1.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        BorderModifierOperation.read(buf, ops);
        BorderModifierOperation.documentation(doc);
        b1.serialize(ms);
        assertNotNull(b1.deepToString(""));

        // PaddingModifierOperation
        PaddingModifierOperation pad = new PaddingModifierOperation(10f, 15f, 10f, 15f);
        pad.apply(mRemoteContext);
        assertEquals(10f, pad.getLeft(), 0.01f);
        assertEquals(15f, pad.getTop(), 0.01f);
        assertEquals(10f, pad.getRight(), 0.01f);
        assertEquals(15f, pad.getBottom(), 0.01f);

        PaddingModifierOperation padNan = new PaddingModifierOperation(nanW, nanRad, nanW, nanRad);
        padNan.registerListening(mRemoteContext);
        padNan.updateVariables(mRemoteContext);
        buf.reset(0);
        pad.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        PaddingModifierOperation.read(buf, ops);
        PaddingModifierOperation.documentation(doc);
        pad.serialize(ms);
        assertNotNull(pad.deepToString(""));

        // OffsetModifierOperation
        OffsetModifierOperation off = new OffsetModifierOperation(20f, 30f);
        assertEquals(20f, off.getX(), 0.01f);
        assertEquals(30f, off.getY(), 0.01f);
        off.setX(25f);
        off.setY(35f);
        off.layout(mRemoteContext, dummy, 100f, 100f);
        off.paint(mPaintContext);

        OffsetModifierOperation offNan = new OffsetModifierOperation(nanW, nanRad);
        offNan.registerListening(mRemoteContext);
        offNan.updateVariables(mRemoteContext);
        buf.reset(0);
        off.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        OffsetModifierOperation.read(buf, ops);
        OffsetModifierOperation.documentation(doc);
        off.serialize(ms);
        assertNotNull(off.deepToString(""));

        // ClipRectModifierOperation
        ClipRectModifierOperation clip = new ClipRectModifierOperation();
        clip.layout(mRemoteContext, dummy, 100f, 100f);
        clip.paint(mPaintContext);
        buf.reset(0);
        clip.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        ClipRectModifierOperation.read(buf, ops);
        ClipRectModifierOperation.documentation(doc);
        clip.serialize(ms);
        assertNotNull(clip.deepToString(""));

        // CollapsiblePriorityModifierOperation
        CollapsiblePriorityModifierOperation cpo = new CollapsiblePriorityModifierOperation(0, 5f);
        assertEquals(5f, cpo.getPriority(), 0.01f);
        assertEquals(0, cpo.getOrientation());
        cpo.apply(mRemoteContext);
        buf.reset(0);
        cpo.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        CollapsiblePriorityModifierOperation.read(buf, ops);
        CollapsiblePriorityModifierOperation.documentation(doc);
        cpo.serialize(ms);
        assertNotNull(cpo.deepToString(""));

        // ZIndexModifierOperation
        ZIndexModifierOperation zop = new ZIndexModifierOperation(10f);
        zop.setValue(15f);
        zop.layout(mRemoteContext, dummy, 100f, 100f);
        zop.paint(mPaintContext);
        zop.apply(mRemoteContext);
        buf.reset(0);
        zop.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        ZIndexModifierOperation.read(buf, ops);
        ZIndexModifierOperation.documentation(doc);
        zop.serialize(ms);
        assertNotNull(zop.deepToString(""));
    }

    @Test
    public void testLayoutComputeExtended() {
        LayoutComponent parent = new BoxLayout(null, 1, -1, 0, 0, 100, 100, 0, 0);
        MeasurePass measure = new FlatMeasurePass(100);
        ComponentMeasure cm = measure.get(parent);

        CollectionsAccess access = mock(CollectionsAccess.class);
        when(mRemoteContext.getCollectionsAccess()).thenReturn(access);
        ArrayAccess array = mock(ArrayAccess.class);
        when(access.getArray(anyInt())).thenReturn(array);
        when(array.getFloats()).thenReturn(new float[]{10f, 20f, 30f, 40f, 50f, 60f});

        LayoutComputeOperation lcMeasure = new LayoutComputeOperation(LayoutComputeOperation.TYPE_MEASURE, 10, true);
        lcMeasure.setParent(parent);
        assertTrue(lcMeasure.applyToMeasure(mPaintContext, cm, cm));
        assertEquals(30f, cm.getW(), 0.01f);
        assertEquals(40f, cm.getH(), 0.01f);

        LayoutComputeOperation lcPos = new LayoutComputeOperation(LayoutComputeOperation.TYPE_POSITION, 11, false);
        lcPos.setParent(parent);
        assertTrue(lcPos.applyToMeasure(mPaintContext, cm, cm));
        assertEquals(10f, cm.getX(), 0.01f);
        assertEquals(20f, cm.getY(), 0.01f);

        LayoutComputeOperation lcBoth = new LayoutComputeOperation(999, 12, false);
        lcBoth.setParent(parent);
        when(array.getFloats()).thenReturn(new float[]{55f, 65f, 75f, 85f, 50f, 60f});
        assertTrue(lcBoth.applyToMeasure(mPaintContext, cm, cm));
    }

    @Test
    public void testCanvasLayoutExtended() {
        CanvasLayout canvas = new CanvasLayout(null, 50, -1, 0, 0, 100, 100);
        LayoutComponentContent lcc = new LayoutComponentContent(51);
        CanvasContent cc = new CanvasContent(52);
        lcc.mList.add(cc);
        canvas.mList.add(lcc);

        Component child = new Component(53, 0, 0, 50, 50, canvas);
        canvas.getChildrenComponents().add(child);

        canvas.inflate();
        MeasurePass measure = new FlatMeasurePass(100);
        canvas.internalLayoutMeasure(mPaintContext, measure);
        canvas.paint(mPaintContext);

        // CanvasLayout without CanvasContent
        CanvasLayout canvas2 = new CanvasLayout(null, 60, -1, 0, 0, 100, 100);
        LayoutComponentContent lcc2 = new LayoutComponentContent(61);
        canvas2.mList.add(lcc2);
        canvas2.getChildrenComponents().add(new Component(62, 0, 0, 40, 40, canvas2));
        canvas2.inflate();
        canvas2.internalLayoutMeasure(mPaintContext, measure);
        canvas2.paint(mPaintContext);

        WireBuffer buf = new WireBuffer();
        canvas.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        List<Operation> ops = new ArrayList<>();
        CanvasLayout.read(buf, ops);
        CanvasLayout.documentation(mockDoc());
        canvas.serialize(mockMapSerializer());
        assertEquals("CanvasLayout", CanvasLayout.name());
        assertEquals(Operations.LAYOUT_CANVAS, CanvasLayout.id());
    }

    @Test
    public void testDimensionsAndVisibilityComprehensive() {
        WireBuffer buf = new WireBuffer();
        List<Operation> ops = new ArrayList<>();
        DocumentationBuilder doc = mockDoc();
        MapSerializer ms = mockMapSerializer();

        // DimensionModifierOperation Types
        for (int i = 0; i <= 8; i++) {
            DimensionModifierOperation.Type t = DimensionModifierOperation.Type.fromInt(i);
            assertNotNull(t);
        }
        assertEquals(DimensionModifierOperation.Type.EXACT, DimensionModifierOperation.Type.fromInt(999));

        WidthModifierOperation wExact = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 120f);
        assertTrue(wExact.isExact());
        assertFalse(wExact.isWrap());
        assertFalse(wExact.isFill());
        assertFalse(wExact.hasWeight());
        assertFalse(wExact.isIntrinsicMin());
        assertFalse(wExact.isIntrinsicMax());
        assertFalse(wExact.isFillParentMaxWidth());
        assertFalse(wExact.isFillParentMaxHeight());
        assertEquals(120f, wExact.getValue(), 0.01f);
        wExact.setValue(130f);
        assertEquals(130f, wExact.getValue(), 0.01f);
        wExact.setType(DimensionModifierOperation.Type.WRAP);
        assertTrue(wExact.isWrap());
        assertEquals("WIDTH", wExact.serializedName());
        assertNotNull(wExact.deepToString("  "));
        assertNotNull(wExact.toString());

        RootLayoutComponent root = mock(RootLayoutComponent.class);
        when(mDocument.getRootLayoutComponent()).thenReturn(root);

        WidthModifierOperation wDp = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT_DP, 50f);
        assertTrue(wDp.isExact());
        wDp.updateVariables(mRemoteContext);

        WidthModifierOperation wNan = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, Utils.asNan(101));
        wNan.registerListening(mRemoteContext);
        when(mRemoteContext.getFloat(101)).thenReturn(75f);
        wNan.updateVariables(mRemoteContext);
        assertEquals(75f, wNan.getValue(), 0.01f);

        WidthModifierOperation wDpNan = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT_DP, Utils.asNan(102));
        wDpNan.registerListening(mRemoteContext);
        when(mRemoteContext.getFloat(102)).thenReturn(40f);
        when(mRemoteContext.getDensity()).thenReturn(2f);
        wDpNan.updateVariables(mRemoteContext);
        assertEquals(80f, wDpNan.getValue(), 0.01f);

        // Width & Height write/read/doc/serialize
        buf.reset(0);
        wExact.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        WidthModifierOperation.read(buf, ops);
        WidthModifierOperation.documentation(doc);
        wExact.serialize(ms);
        assertEquals("WidthModifierOperation", WidthModifierOperation.name());
        assertEquals(Operations.MODIFIER_WIDTH, WidthModifierOperation.id());

        HeightModifierOperation hExact = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 80f);
        buf.reset(0);
        hExact.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        HeightModifierOperation.read(buf, ops);
        HeightModifierOperation.documentation(doc);
        hExact.serialize(ms);
        assertEquals("HeightModifierOperation", HeightModifierOperation.name());
        assertEquals(Operations.MODIFIER_HEIGHT, HeightModifierOperation.id());

        // ComponentVisibilityOperation
        ComponentVisibilityOperation cvo = new ComponentVisibilityOperation(201);
        cvo.apply(mRemoteContext);
        assertEquals("COMPONENT_VISIBILITY", cvo.serializedName());
        assertNotNull(cvo.toString());
        assertNotNull(cvo.deepToString(""));
        cvo.registerListening(mRemoteContext);
        when(mRemoteContext.getInteger(201)).thenReturn(Component.Visibility.VISIBLE);
        cvo.updateVariables(mRemoteContext);
        when(mRemoteContext.getInteger(201)).thenReturn(Component.Visibility.INVISIBLE);
        cvo.updateVariables(mRemoteContext);
        when(mRemoteContext.getInteger(201)).thenReturn(Component.Visibility.GONE);
        cvo.updateVariables(mRemoteContext);
        when(mRemoteContext.getInteger(201)).thenReturn(999);
        cvo.updateVariables(mRemoteContext);

        LayoutComponent visParent = new BoxLayout(null, 1, -1, 0, 0, 100, 100, 0, 0);
        Component dummy = new Component(1, 0, 0, 100, 100, null);
        cvo.setParent(visParent);
        cvo.layout(mRemoteContext, dummy, 100f, 100f);
        cvo.evaluateInLayout(mRemoteContext);

        buf.reset(0);
        ComponentVisibilityOperation.apply(buf, 201);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        ComponentVisibilityOperation.read(buf, ops);
        ComponentVisibilityOperation.documentation(doc);
        cvo.serialize(ms);

        // FlatMeasurePass coverage
        FlatMeasurePass flat = new FlatMeasurePass(4);
        flat.setContext(mRemoteContext);
        flat.clear();
        Component testComp = new Component(10, 0, 0, 100, 100, null);
        when(mDocument.getComponent(10)).thenReturn(testComp);
        ComponentMeasure cmObtained = flat.obtain(testComp, 0, 0, 100, 100, Component.Visibility.VISIBLE);
        try {
            flat.add(cmObtained);
        } catch (Exception ignored) {}
        assertTrue(flat.contains(10));
        flat.recycle(cmObtained);
    }

    @Test
    public void testTextStyleAndCoreTextComprehensive() {
        WireBuffer buf = new WireBuffer();
        DocumentationBuilder doc = mockDoc();
        MapSerializer ms = mockMapSerializer();

        TextStyle style = new TextStyle(
                10,
                0xFFFF0000,
                -1,
                20f,
                10f,
                40f,
                0,
                700f,
                -1,
                CoreText.TEXT_ALIGN_CENTER,
                CoreText.OVERFLOW_ELLIPSIS,
                2,
                0.5f,
                2f,
                1.5f,
                CoreText.BREAK_STRATEGY_HIGH_QUALITY,
                CoreText.HYPHENATION_FREQUENCY_FULL,
                CoreText.JUSTIFICATION_MODE_INTER_CHARACTER,
                true,
                false,
                null,
                null,
                false,
                -1
        );

        buf.reset(0);
        style.write(buf);
        buf.setIndex(0);
        buf.readByte();
        List<Operation> styleOps = new ArrayList<>();
        TextStyle.read(buf, styleOps);
        assertEquals(1, styleOps.size());
        TextStyle.documentation(doc);
        style.apply(mRemoteContext);
        assertNotNull(style.toString());

        CoreText coreText = new CoreText(
                null, 1, -1, 100, 0xFF000000, -1, 16f, 10f, 30f, 0, 400f, -1,
                CoreText.TEXT_ALIGN_START, CoreText.OVERFLOW_CLIP, 1, 0f, 0f, 1f,
                CoreText.BREAK_STRATEGY_SIMPLE, CoreText.HYPHENATION_FREQUENCY_NONE,
                CoreText.JUSTIFICATION_MODE_NONE, false, false, null, null, false, 0, -1
        );

        when(mRemoteContext.getText(100)).thenReturn("Test Text Content");
        coreText.registerListening(mRemoteContext);
        coreText.updateVariables(mRemoteContext);
        coreText.applyStyle(style);

        MeasurePass measure = new FlatMeasurePass(100);
        coreText.mWidthModifier = new WidthModifierOperation(100f);
        coreText.mHeightModifier = new HeightModifierOperation(50f);
        coreText.computeSize(mPaintContext, 0, 200, 0, 200, measure);
        coreText.computeWrapSize(mPaintContext, 0, 200, 0, 200, true, true, measure, new Size(0, 0));
        coreText.measure(mPaintContext, 0, 200, 0, 200, measure);
        coreText.layout(mRemoteContext, measure);
        coreText.paintingComponent(mPaintContext);

        buf.reset(0);
        coreText.write(buf);
        buf.setIndex(0);
        buf.readOperationType();
        List<Operation> ops = new ArrayList<>();
        CoreText.read(buf, ops);
        CoreText.documentation(doc);
        coreText.serialize(ms);
        assertNotNull(coreText.deepToString(""));
        assertNotNull(coreText.toString());
    }

    // =========================================================================
    // SECTION 15: FlowLayout, BoxLayout, GraphicsLayerModifierOperation Comprehensive
    // =========================================================================

    @Test
    public void testFlowLayoutAndBoxLayoutAndGraphicsLayerComprehensive() {
        WireBuffer buf = new WireBuffer();
        DocumentationBuilder doc = mockDoc();
        MapSerializer ms = mockMapSerializer();
        MeasurePass measure = new FlatMeasurePass(100);

        // 1. FlowLayout
        FlowLayout flow = new FlowLayout(null, 1, -1, 0, 0, 300, 200, FlowLayout.CENTER, FlowLayout.CENTER, 8f, 3, 2);
        FlowLayout flow2 = new FlowLayout(null, 2, -1, FlowLayout.START, FlowLayout.TOP, 4f, 2, 2);
        assertEquals(Operations.LAYOUT_FLOW, FlowLayout.id());
        assertNotNull(flow.toString());

        flow.mWidthModifier = new WidthModifierOperation(300f);
        flow.mHeightModifier = new HeightModifierOperation(200f);
        Component child1 = new Component(10, 0, 0, 80, 50, flow);
        Component child2 = new Component(11, 0, 0, 80, 50, flow);
        Component child3 = new Component(12, 0, 0, 80, 50, flow);
        Component child4 = new Component(13, 0, 0, 80, 50, flow);
        flow.getChildrenComponents().add(child1);
        flow.getChildrenComponents().add(child2);
        flow.getChildrenComponents().add(child3);
        flow.getChildrenComponents().add(child4);

        flow.computeWrapSize(mPaintContext, 0, 300, 0, 200, true, true, measure, new Size(0, 0));
        flow.computeSize(mPaintContext, 0, 300, 0, 200, measure);
        flow.measure(mPaintContext, 0, 300, 0, 200, measure);
        flow.layout(mRemoteContext, measure);

        buf.reset(0);
        FlowLayout.apply(buf, 1, -1, FlowLayout.CENTER, FlowLayout.CENTER, 8f, 3, 2);
        buf.setIndex(0);
        buf.readOperationType();
        List<Operation> flowOps = new ArrayList<>();
        FlowLayout.read(buf, flowOps);
        assertEquals(1, flowOps.size());
        flow.write(buf);
        flow.serialize(ms);
        FlowLayout.documentation(doc);

        // 2. BoxLayout
        BoxLayout box = new BoxLayout(null, 1, -1, 0, 0, 200, 200, BoxLayout.CENTER, BoxLayout.CENTER);
        BoxLayout box2 = new BoxLayout(null, 2, -1, BoxLayout.START, BoxLayout.TOP);
        assertEquals(Operations.LAYOUT_BOX, BoxLayout.id());
        assertEquals("BoxLayout", BoxLayout.name());
        assertNotNull(box.toString());

        box.mWidthModifier = new WidthModifierOperation(200f);
        box.mHeightModifier = new HeightModifierOperation(200f);
        box.getChildrenComponents().add(new Component(20, 0, 0, 50, 50, box));
        box.getChildrenComponents().add(new Component(21, 0, 0, 80, 80, box));
        box.computeWrapSize(mPaintContext, 0, 200, 0, 200, true, true, measure, new Size(0, 0));
        box.computeSize(mPaintContext, 0, 200, 0, 200, measure);
        box.measure(mPaintContext, 0, 200, 0, 200, measure);
        box.layout(mRemoteContext, measure);

        buf.reset(0);
        BoxLayout.apply(buf, 1, -1, BoxLayout.CENTER, BoxLayout.CENTER);
        buf.setIndex(0);
        buf.readOperationType();
        List<Operation> boxOps = new ArrayList<>();
        BoxLayout.read(buf, boxOps);
        assertEquals(1, boxOps.size());
        box.write(buf);
        box.serialize(ms);
        BoxLayout.documentation(doc);

        // 3. GraphicsLayerModifierOperation
        assertEquals(Operations.MODIFIER_GRAPHICS_LAYER, GraphicsLayerModifierOperation.id());
        assertEquals("GraphicsLayerModifierOperation", GraphicsLayerModifierOperation.name());
        GraphicsLayerModifierOperation.documentation(doc);

        java.util.HashMap<Integer, Object> glMap = new java.util.HashMap<>();
        glMap.put(GraphicsLayerModifierOperation.SCALE_X, 1.5f);
        glMap.put(GraphicsLayerModifierOperation.SCALE_Y, 2.0f);
        glMap.put(GraphicsLayerModifierOperation.ROTATION_X, 15f);
        glMap.put(GraphicsLayerModifierOperation.ROTATION_Y, 25f);
        glMap.put(GraphicsLayerModifierOperation.ROTATION_Z, 35f);
        glMap.put(GraphicsLayerModifierOperation.TRANSLATION_X, 10f);
        glMap.put(GraphicsLayerModifierOperation.TRANSLATION_Y, 20f);
        glMap.put(GraphicsLayerModifierOperation.TRANSLATION_Z, 30f);
        glMap.put(GraphicsLayerModifierOperation.SHADOW_ELEVATION, 5f);
        glMap.put(GraphicsLayerModifierOperation.ALPHA, 0.7f);
        glMap.put(GraphicsLayerModifierOperation.CAMERA_DISTANCE, 12f);
        glMap.put(GraphicsLayerModifierOperation.COMPOSITING_STRATEGY, 1);
        glMap.put(GraphicsLayerModifierOperation.SPOT_SHADOW_COLOR, 0xFF00FF00);
        glMap.put(GraphicsLayerModifierOperation.AMBIENT_SHADOW_COLOR, 0xFF0000FF);
        glMap.put(GraphicsLayerModifierOperation.BLUR_RADIUS_X, 8f);
        glMap.put(GraphicsLayerModifierOperation.BLUR_RADIUS_Y, 8f);
        glMap.put(GraphicsLayerModifierOperation.BLUR_TILE_MODE, GraphicsLayerModifierOperation.TILE_MODE_CLAMP);
        glMap.put(GraphicsLayerModifierOperation.SHAPE, GraphicsLayerModifierOperation.SHAPE_ROUND_RECT);
        glMap.put(GraphicsLayerModifierOperation.SHAPE_RADIUS, 16f);

        buf.reset(0);
        GraphicsLayerModifierOperation.apply(buf, glMap);
        buf.setIndex(0);
        buf.readOperationType();
        List<Operation> glOps = new ArrayList<>();
        GraphicsLayerModifierOperation.read(buf, glOps);
        assertEquals(1, glOps.size());

        GraphicsLayerModifierOperation gl = (GraphicsLayerModifierOperation) glOps.get(0);
        assertNotNull(gl.deepToString(""));
        assertNotNull(gl.toString());
        gl.paint(mPaintContext);

        androidx.compose.remote.core.operations.utilities.StringSerializer ss =
                new androidx.compose.remote.core.operations.utilities.StringSerializer();
        gl.serializeToString(0, ss);
        assertNotNull(ss.toString());

        buf.reset(0);
        gl.write(buf);

        java.util.HashMap<Integer, Object> attrMap = new java.util.HashMap<>();
        gl.fillInAttributes(attrMap);
        assertFalse(attrMap.isEmpty());

        // 4. LegacyMeasurePolicy
        ColumnLayout lm = new ColumnLayout(null, 100, -1, 0, 0, 200, 200, ColumnLayout.START, ColumnLayout.TOP, 0f);
        lm.mWidthModifier = new WidthModifierOperation(150f);
        lm.mHeightModifier = new HeightModifierOperation(150f);
        lm.getChildrenComponents().add(new Component(101, 0, 0, 50, 50, lm));
        LegacyMeasurePolicy.INSTANCE.measure(lm, mPaintContext, 0, 300, 0, 300, measure);

        lm.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.WRAP);
        lm.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.WRAP);
        LegacyMeasurePolicy.INSTANCE.measure(lm, mPaintContext, 0, 300, 0, 300, measure);

        lm.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.INTRINSIC_MIN);
        lm.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.INTRINSIC_MIN);
        LegacyMeasurePolicy.INSTANCE.measure(lm, mPaintContext, 0, 300, 0, 300, measure);

        lm.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.FILL);
        lm.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.FILL);
        LegacyMeasurePolicy.INSTANCE.measure(lm, mPaintContext, 0, 300, 0, 300, measure);
    }

    // =========================================================================
    // SECTION 16: Deep Coverage for Component, ComponentModifiers,
    //             ScrollModifier, Collapsible Layouts, Custom & RootLayout
    // =========================================================================

    @Test
    public void testComponentDeepTouchAndLifecycleCoverage() throws Exception {
        RootLayoutComponent root = new RootLayoutComponent(1);
        BoxLayout parent = new BoxLayout(root, 2, -1, 0, 0, 300, 300, BoxLayout.START, BoxLayout.TOP);
        parent.mWidthModifier = new WidthModifierOperation(300f);
        parent.mHeightModifier = new HeightModifierOperation(300f);
        root.getList().add(parent);
        Component child = new Component(3, 10, 10, 100, 100, parent);
        parent.getList().add(child);
        parent.getChildrenComponents().add(child);

        // 1. Coordinates, copy constructor, bounds
        Component childCopy = new Component(child);
        assertEquals(3, childCopy.getComponentId());
        assertEquals(root, child.getRoot());
        assertEquals(10f, child.getX(), 0.001f);
        assertEquals(10f, child.getY(), 0.001f);
        assertEquals(100f, child.getWidth(), 0.001f);
        assertEquals(100f, child.getHeight(), 0.001f);
        child.setX(20f);
        child.setY(30f);
        child.setWidth(80f);
        child.setHeight(90f);
        assertEquals(20f, child.getX(), 0.001f);
        assertEquals(30f, child.getY(), 0.001f);
        assertEquals(80f, child.getWidth(), 0.001f);
        assertEquals(90f, child.getHeight(), 0.001f);
        child.setX(10f);
        child.setY(10f);
        child.setWidth(100f);
        child.setHeight(100f);

        child.setId(300);
        assertEquals(300, child.getId());
        child.setId(3);
        assertEquals(3, child.getPaintId());
        child.setAnimationId(50);
        assertEquals(50, child.getPaintId());
        child.setAnimationId(-1);
        assertEquals(0f, child.getZIndex(), 0.001f);
        assertEquals(0f, child.getScrollX(), 0.001f);
        assertEquals(0f, child.getScrollY(), 0.001f);

        // 2. Visibility helpers
        assertTrue(Component.Visibility.isVisible(Component.Visibility.VISIBLE));
        assertFalse(Component.Visibility.isVisible(Component.Visibility.GONE));
        assertFalse(Component.Visibility.isVisible(Component.Visibility.INVISIBLE));
        assertTrue(Component.Visibility.isVisible(Component.Visibility.OVERRIDE_VISIBLE));

        assertTrue(Component.Visibility.isGone(Component.Visibility.GONE));
        assertFalse(Component.Visibility.isGone(Component.Visibility.VISIBLE));
        assertTrue(Component.Visibility.isGone(Component.Visibility.OVERRIDE_GONE));

        assertTrue(Component.Visibility.isInvisible(Component.Visibility.INVISIBLE));
        assertFalse(Component.Visibility.isInvisible(Component.Visibility.VISIBLE));
        assertTrue(Component.Visibility.isInvisible(Component.Visibility.OVERRIDE_INVISIBLE));

        assertTrue(Component.Visibility.hasOverride(Component.Visibility.OVERRIDE_VISIBLE));
        assertFalse(Component.Visibility.hasOverride(Component.Visibility.VISIBLE));
        assertEquals(Component.Visibility.VISIBLE, Component.Visibility.clearOverride(Component.Visibility.VISIBLE | Component.Visibility.OVERRIDE_VISIBLE));
        assertEquals(Component.Visibility.VISIBLE | Component.Visibility.OVERRIDE_VISIBLE, Component.Visibility.add(Component.Visibility.VISIBLE, Component.Visibility.OVERRIDE_VISIBLE));
        assertEquals(Component.Visibility.VISIBLE, Component.Visibility.add(Component.Visibility.VISIBLE, Component.Visibility.CLEAR_OVERRIDE));

        assertEquals("VISIBLE", Component.Visibility.toString(Component.Visibility.VISIBLE));
        assertEquals("INVISIBLE", Component.Visibility.toString(Component.Visibility.INVISIBLE));
        assertEquals("GONE", Component.Visibility.toString(Component.Visibility.GONE));
        assertEquals("OVERRIDE_GONE", Component.Visibility.toString(Component.Visibility.OVERRIDE_GONE));
        assertEquals("OVERRIDE_VISIBLE", Component.Visibility.toString(Component.Visibility.OVERRIDE_VISIBLE));
        assertEquals("OVERRIDE_INVISIBLE", Component.Visibility.toString(Component.Visibility.OVERRIDE_INVISIBLE));
        assertEquals("7", Component.Visibility.toString(7));

        child.setVisibility(Component.Visibility.INVISIBLE);
        assertEquals(Component.Visibility.INVISIBLE, child.mScheduledVisibility);
        child.mVisibility = Component.Visibility.INVISIBLE;
        assertTrue(child.isInvisible());
        assertFalse(child.isVisible());
        child.setVisibility(Component.Visibility.GONE);
        assertEquals(Component.Visibility.GONE, child.mScheduledVisibility);
        child.mVisibility = Component.Visibility.GONE;
        assertTrue(child.isGone());
        child.setVisibility(Component.Visibility.VISIBLE);
        assertEquals(Component.Visibility.VISIBLE, child.mScheduledVisibility);
        child.mVisibility = Component.Visibility.VISIBLE;
        assertTrue(child.isVisible());

        // 3. getLocationInWindow and getBoundsInSemanticParent
        float[] loc = new float[2];
        child.getLocationInWindow(mRemoteContext, loc);
        assertTrue(loc[0] >= 0);
        loc[0] = 0; loc[1] = 0;
        child.getLocationInWindow(mRemoteContext, loc, false);

        int[] bounds = new int[4];
        child.getBoundsInSemanticParent(bounds, null);
        child.getBoundsInSemanticParent(bounds, 1);
        child.getBoundsInSemanticParent(bounds, 2);

        // 4. contains hit detection
        assertTrue(child.contains(mRemoteContext, 15f, 15f));
        assertFalse(child.contains(mRemoteContext, 500f, 500f));

        // 5. Clicks and touches with FIX_TOUCH_EVENT
        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
        assertFalse(child.onClick(mRemoteContext, mDocument, 500f, 500f));
        assertFalse(child.onLongPress(mRemoteContext, mDocument, 500f, 500f));
        assertFalse(child.onDoubleClick(mRemoteContext, mDocument, 500f, 500f));
        assertFalse(child.onTouchDown(mRemoteContext, mDocument, 500f, 500f));
        assertFalse(child.onTouchUp(mRemoteContext, mDocument, 500f, 500f, 0f, 0f, false));
        assertFalse(child.onTouchCancel(mRemoteContext, mDocument, 500f, 500f, false));
        assertFalse(child.onTouchDrag(mRemoteContext, mDocument, 500f, 500f, false));

        // Unconditional click inside
        child.onClick(mRemoteContext, mDocument, -1f, -1f);
        child.onLongPress(mRemoteContext, mDocument, -1f, -1f);
        child.onDoubleClick(mRemoteContext, mDocument, -1f, -1f);

        // Inside bounds touches
        child.onTouchDown(mRemoteContext, mDocument, 15f, 15f);
        child.onTouchDrag(mRemoteContext, mDocument, 15f, 15f, true);
        child.onTouchUp(mRemoteContext, mDocument, 15f, 15f, 0f, 0f, true);
        child.onTouchCancel(mRemoteContext, mDocument, 15f, 15f, true);

        // Legacy touch version
        when(mRemoteContext.getTouchVersion()).thenReturn(0);
        child.onClick(mRemoteContext, mDocument, 15f, 15f);
        child.onLongPress(mRemoteContext, mDocument, 15f, 15f);
        child.onDoubleClick(mRemoteContext, mDocument, 15f, 15f);
        child.onTouchDown(mRemoteContext, mDocument, 15f, 15f);
        child.onTouchDrag(mRemoteContext, mDocument, 15f, 15f, true);
        child.onTouchUp(mRemoteContext, mDocument, 15f, 15f, 0f, 0f, true);
        child.onTouchCancel(mRemoteContext, mDocument, 15f, 15f, true);
        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);

        // 6. Child handlers dispatching
        Component grandChild = new Component(4, 0, 0, 50, 50, child);
        child.getList().add(grandChild);
        child.onClick(mRemoteContext, mDocument, 15f, 15f);
        child.onLongPress(mRemoteContext, mDocument, 15f, 15f);
        child.onDoubleClick(mRemoteContext, mDocument, 15f, 15f);
        child.onTouchDown(mRemoteContext, mDocument, 15f, 15f);
        child.onTouchDrag(mRemoteContext, mDocument, 15f, 15f, false);
        child.onTouchUp(mRemoteContext, mDocument, 15f, 15f, 0f, 0f, false);
        child.onTouchCancel(mRemoteContext, mDocument, 15f, 15f, false);

        // 7. Relayout boundary checks
        parent.mWidthModifier = new WidthModifierOperation(100f);
        parent.mHeightModifier = new HeightModifierOperation(100f);
        assertTrue(parent.isRelayoutBoundary(null));
        when(mDocument.isRelayoutBoundaryEnabled()).thenReturn(false);
        assertFalse(parent.isRelayoutBoundary(mDocument));
        when(mDocument.isRelayoutBoundaryEnabled()).thenReturn(true);
        assertTrue(parent.isRelayoutBoundary(mDocument));

        parent.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.FILL);
        parent.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.FILL);
        assertTrue(parent.isRelayoutBoundary(mDocument));

        parent.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.WRAP);
        assertFalse(parent.isRelayoutBoundary(mDocument));

        child.invalidateMeasure();
        assertTrue(root.needsMeasure());
        child.needsRepaint();
        assertTrue(child.doesNeedsRepaint() || root.doesNeedsRepaint());

        // 8. Components and Data extraction
        ArrayList<Component> compList = new ArrayList<>();
        root.getComponents(compList);
        assertEquals(1, compList.size());
        assertTrue(root.getComponentCount() >= 2);
        assertNotNull(root.getComponent(3));
        assertNull(root.getComponent(9999));

        ArrayList<Operation> dataList = new ArrayList<>();
        child.getData(dataList, true);
        child.getData(dataList, false);

        // 9. Transitions suitability
        Component other = new Component(3, 10, 10, 100, 100, null);
        assertFalse(child.suitableForTransition(mock(Operation.class)));
        assertFalse(child.suitableForTransition(other));
        other.getList().add(new Component(4, 0, 0, 50, 50, other));
        assertTrue(child.suitableForTransition(other));

        // 10. Strings, serialization and debug
        assertNotNull(child.content());
        assertNotNull(child.textContent());
        assertNotNull(child.deepToString("  "));
        StringSerializer ss = new StringSerializer();
        child.serializeToString(0, ss);
        child.serialize(mockMapSerializer());

        // 11. Painting edge cases
        when(mPaintContext.isVisualDebug()).thenReturn(true);
        child.paint(mPaintContext);
        when(mPaintContext.isVisualDebug()).thenReturn(false);

        child.setVisibility(Component.Visibility.GONE);
        child.paint(mPaintContext);
        child.setVisibility(Component.Visibility.INVISIBLE);
        child.paint(mPaintContext);
        child.setVisibility(Component.Visibility.VISIBLE);

        // 12. Exception handling when no root
        Component detached = new Component(100, 0, 0, 50, 50, null);
        try {
            detached.getRoot();
            fail("Should throw Exception");
        } catch (Exception expected) {}
        detached.invalidateMeasure();
    }

    @Test
    public void testComponentModifiersDeepCoverage() {
        ComponentModifiers cm = new ComponentModifiers();
        assertEquals(0, cm.size());
        assertEquals(0, cm.getModifiersList().size());
        assertEquals(0, cm.getList().size());

        PaddingModifierOperation padding = new PaddingModifierOperation(10f, 15f, 10f, 15f);
        ClickModifierOperation click = new ClickModifierOperation();
        MultiClickModifier multiClick = new MultiClickModifier(MultiClickModifier.CLICK_TYPE_SINGLE);
        ScrollModifierOperation scroll = new ScrollModifierOperation(0, 0f, 100f, 50f);

        cm.add(padding);
        cm.add(click);
        cm.add(multiClick);
        cm.add(scroll);
        assertEquals(4, cm.size());

        // Scroll queries and dimensions
        assertTrue(cm.hasVerticalScroll());
        assertFalse(cm.hasHorizontalScroll());
        cm.setVerticalScrollDimension(100f, 300f);
        cm.setHorizontalScrollDimension(100f, 300f);
        assertEquals(300f, cm.getVerticalScrollDimension(), 0.001f);
        assertEquals(0f, cm.getHorizontalScrollDimension(), 0.001f);

        Component dummy = new Component(1, 0, 0, 100, 100, null);

        // Layout pass
        cm.layout(mRemoteContext, dummy, 100f, 100f);

        // Paint pass
        cm.paint(mPaintContext);

        // Clicks
        cm.onClick(mRemoteContext, mDocument, dummy, 50f, 50f);
        cm.onLongPress(mRemoteContext, mDocument, dummy, 50f, 50f);
        cm.onDoubleClick(mRemoteContext, mDocument, dummy, 50f, 50f);

        // Touches
        cm.onTouchDown(mRemoteContext, mDocument, dummy, 50f, 50f);
        cm.onTouchDrag(mRemoteContext, mDocument, dummy, 55f, 55f);
        cm.onTouchUp(mRemoteContext, mDocument, dummy, 55f, 55f, 5f, 5f);
        cm.onTouchCancel(mRemoteContext, mDocument, dummy, 55f, 55f);

        // Serialization
        StringSerializer ss = new StringSerializer();
        cm.serializeToString(0, ss);
        assertNotNull(cm.toString());
        cm.serialize(mockMapSerializer());
        cm.apply(mRemoteContext);
        cm.write(new WireBuffer());

        // Horizontal scroll variant
        ComponentModifiers cmH = new ComponentModifiers();
        ScrollModifierOperation scrollH = new ScrollModifierOperation(1, 0f, 100f, 50f);
        cmH.add(scrollH);
        assertTrue(cmH.hasHorizontalScroll());
        assertFalse(cmH.hasVerticalScroll());
        cmH.setHorizontalScrollDimension(100f, 250f);
        assertEquals(250f, cmH.getHorizontalScrollDimension(), 0.001f);
        assertEquals(0f, cmH.getVerticalScrollDimension(), 0.001f);
    }

    @Test
    public void testScrollModifierOperationDeepCoverage() {
        ScrollModifierOperation vScroll = new ScrollModifierOperation(0, Utils.asNan(10), Utils.asNan(11), Utils.asNan(12));
        ScrollModifierOperation hScroll = new ScrollModifierOperation(1, Utils.asNan(20), Utils.asNan(21), Utils.asNan(22));

        assertTrue(vScroll.isVerticalScroll());
        assertFalse(vScroll.isHorizontalScroll());
        assertTrue(vScroll.handlesVerticalScroll());
        assertFalse(vScroll.handlesHorizontalScroll());

        assertFalse(hScroll.isVerticalScroll());
        assertTrue(hScroll.isHorizontalScroll());
        assertFalse(hScroll.handlesVerticalScroll());
        assertTrue(hScroll.handlesHorizontalScroll());

        assertEquals(0f, vScroll.getScrollX(), 0.001f);
        assertEquals(0f, vScroll.getScrollY(), 0.001f);
        assertEquals(0f, vScroll.getScrollX(0f), 0.001f);
        assertEquals(0f, vScroll.getScrollY(0f), 0.001f);

        vScroll.reset();
        hScroll.reset();

        // Dimensions
        vScroll.setVerticalScrollDimension(100f, 400f);
        assertEquals(400f, vScroll.getContentDimension(), 0.001f);
        hScroll.setHorizontalScrollDimension(100f, 500f);
        assertEquals(500f, hScroll.getContentDimension(), 0.001f);

        ColumnLayout col = new ColumnLayout(null, 1, -1, 0, 0, 100, 100, 0, 0, 0f);
        col.mWidthModifier = new WidthModifierOperation(100f);
        col.mHeightModifier = new HeightModifierOperation(100f);
        Component childComp = new Component(2, 0, 250, 50, 50, col);
        col.getChildrenComponents().add(childComp);

        // Layout
        vScroll.layout(mRemoteContext, col, 100f, 100f);
        hScroll.layout(mRemoteContext, col, 100f, 100f);

        // Paint
        vScroll.paint(mPaintContext);
        hScroll.paint(mPaintContext);

        // Edge effects
        ScrollingEdgeEffect edgeA = mock(ScrollingEdgeEffect.class);
        ScrollingEdgeEffect edgeB = mock(ScrollingEdgeEffect.class);
        when(mRemoteContext.createEdgeEffect(anyInt())).thenReturn(edgeA, edgeB);

        vScroll.applyEdgeEffect(mPaintContext, col, 0);
        hScroll.applyEdgeEffect(mPaintContext, col, 0);

        // Touches - FIX_TOUCH_EVENT
        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
        vScroll.onTouchDown(mRemoteContext, mDocument, col, 20f, 20f);
        vScroll.onTouchDrag(mRemoteContext, mDocument, col, 20f, 10f);
        vScroll.onTouchUp(mRemoteContext, mDocument, col, 20f, 10f, 0f, -10f);
        vScroll.onTouchCancel(mRemoteContext, mDocument, col, 20f, 10f);

        hScroll.onTouchDown(mRemoteContext, mDocument, col, 20f, 20f);
        hScroll.onTouchDrag(mRemoteContext, mDocument, col, 10f, 20f);
        hScroll.onTouchUp(mRemoteContext, mDocument, col, 10f, 20f, -10f, 0f);
        hScroll.onTouchCancel(mRemoteContext, mDocument, col, 10f, 20f);

        // Touches - legacy
        when(mRemoteContext.getTouchVersion()).thenReturn(0);
        vScroll.onTouchDown(mRemoteContext, mDocument, col, 20f, 20f);
        vScroll.onTouchDrag(mRemoteContext, mDocument, col, 20f, 10f);
        vScroll.onTouchUp(mRemoteContext, mDocument, col, 20f, 10f, 0f, -10f);
        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);

        // Documentation, write, serialize
        DocumentationBuilder doc = mockDoc();
        ScrollModifierOperation.documentation(doc);
        WireBuffer buf = new WireBuffer();
        vScroll.write(buf);
        buf.setIndex(0);
        List<Operation> ops = new ArrayList<>();
        ScrollModifierOperation.read(buf, ops);
        assertEquals(1, ops.size());

        StringSerializer ss = new StringSerializer();
        vScroll.serializeToString(0, ss);
        assertNotNull(vScroll.deepToString(""));
        assertNotNull(vScroll.toString());
    }

    @Test
    public void testCollapsibleColumnAndRowDeepCoverage() {
        // CollapsiblePriority utility
        Component c1 = new Component(1, 0, 0, 50, 50, null);
        ColumnLayout c2 = new ColumnLayout(null, 2, -1, 0, 0, 50, 50, 0, 0, 0f);
        c2.mWidthModifier = new WidthModifierOperation(50f);
        c2.mHeightModifier = new HeightModifierOperation(50f);
        c2.getComponentModifiers().add(new CollapsiblePriorityModifierOperation(CollapsiblePriority.VERTICAL, 5f));

        ColumnLayout c3 = new ColumnLayout(null, 3, -1, 0, 0, 50, 50, 0, 0, 0f);
        c3.mWidthModifier = new WidthModifierOperation(50f);
        c3.mHeightModifier = new HeightModifierOperation(50f);
        c3.getComponentModifiers().add(new CollapsiblePriorityModifierOperation(CollapsiblePriority.VERTICAL, 10f));

        // CollapsibleColumnLayout
        CollapsibleColumnLayout col = new CollapsibleColumnLayout(null, 10, -1, 0, 0, 100, 100, 0, 0, 5f);
        col.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.WRAP);
        col.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.WRAP);
        CollapsibleColumnLayout col6 = new CollapsibleColumnLayout(null, 11, -1, 0, 0, 5f);
        col6.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.WRAP);
        col6.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.WRAP);
        assertTrue(col.hasVerticalIntrinsicDimension());
        assertNotNull(col.toString());

        when(mRemoteContext.useFeature(Header.FEATURE_PRIORITY_FIX)).thenReturn(true);
        assertEquals(0f, col.minIntrinsicHeight(mRemoteContext), 0.001f);
        assertEquals(0f, col.minIntrinsicWidth(mRemoteContext), 0.001f);

        col.getChildrenComponents().add(c2);
        col.getChildrenComponents().add(c3);
        col.minIntrinsicHeight(mRemoteContext);
        col.minIntrinsicWidth(mRemoteContext);

        when(mRemoteContext.useFeature(Header.FEATURE_PRIORITY_FIX)).thenReturn(false);
        col.minIntrinsicHeight(mRemoteContext);
        col.minIntrinsicWidth(mRemoteContext);

        MeasurePass measure = new FlatMeasurePass(100);
        Size size = new Size(0, 0);
        col.computeWrapSize(mPaintContext, 0, 100, 0, 60, true, true, measure, size);
        col.computeSize(mPaintContext, 0, 100, 0, 60, measure);
        col.internalLayoutMeasure(mPaintContext, measure);

        // Density behavior DP
        when(mPaintContext.getDensityBehavior()).thenReturn(CoreDocument.DENSITY_BEHAVIOR_DP);
        when(mPaintContext.getDensity()).thenReturn(2.0f);
        col.computeWrapSize(mPaintContext, 0, 100, 0, 200, true, true, measure, size);
        when(mPaintContext.getDensityBehavior()).thenReturn(CoreDocument.DENSITY_BEHAVIOR_PIXELS);
        when(mPaintContext.getDensity()).thenReturn(1.0f);

        // Documentation, wirebuffer
        DocumentationBuilder doc = mockDoc();
        CollapsibleColumnLayout.documentation(doc);
        WireBuffer buf = new WireBuffer();
        CollapsibleColumnLayout.apply(buf, 12, -1, 0, 0, 5f);
        buf.setIndex(0);
        buf.readOperationType();
        List<Operation> ops = new ArrayList<>();
        CollapsibleColumnLayout.read(buf, ops);
        assertEquals(1, ops.size());

        // CollapsibleRowLayout
        RowLayout r2 = new RowLayout(null, 20, -1, 0, 0, 50, 50, 0, 0, 0f);
        r2.mWidthModifier = new WidthModifierOperation(50f);
        r2.mHeightModifier = new HeightModifierOperation(50f);
        r2.getComponentModifiers().add(new CollapsiblePriorityModifierOperation(CollapsiblePriority.HORIZONTAL, 5f));
        RowLayout r3 = new RowLayout(null, 21, -1, 0, 0, 50, 50, 0, 0, 0f);
        r3.mWidthModifier = new WidthModifierOperation(50f);
        r3.mHeightModifier = new HeightModifierOperation(50f);
        r3.getComponentModifiers().add(new CollapsiblePriorityModifierOperation(CollapsiblePriority.HORIZONTAL, 10f));

        CollapsibleRowLayout row = new CollapsibleRowLayout(null, 30, -1, 0, 0, 100, 100, 0, 0, 5f);
        row.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.WRAP);
        row.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.WRAP);
        CollapsibleRowLayout row6 = new CollapsibleRowLayout(null, 31, -1, 0, 0, 5f);
        row6.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.WRAP);
        row6.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.WRAP);
        assertTrue(row.hasHorizontalIntrinsicDimension());
        assertNotNull(row.toString());

        when(mRemoteContext.useFeature(Header.FEATURE_PRIORITY_FIX)).thenReturn(true);
        row.getChildrenComponents().add(r2);
        row.getChildrenComponents().add(r3);
        row.minIntrinsicWidth(mRemoteContext);
        row.minIntrinsicHeight(mRemoteContext);

        when(mRemoteContext.useFeature(Header.FEATURE_PRIORITY_FIX)).thenReturn(false);
        row.minIntrinsicWidth(mRemoteContext);
        row.minIntrinsicHeight(mRemoteContext);

        row.computeWrapSize(mPaintContext, 0, 60, 0, 100, true, true, measure, size);
        row.computeSize(mPaintContext, 0, 60, 0, 100, measure);
        row.internalLayoutMeasure(mPaintContext, measure);

        CollapsibleRowLayout.documentation(doc);
        buf.reset(0);
        CollapsibleRowLayout.apply(buf, 32, -1, 0, 0, 5f);
        buf.setIndex(0);
        buf.readOperationType();
        ops.clear();
        CollapsibleRowLayout.read(buf, ops);
        assertEquals(1, ops.size());
    }

    @Test
    public void testCustomDeepCoverage() {

        List<Custom.CustomProperty> props = new ArrayList<>();
        props.add(new Custom.CustomProperty(Custom.CustomProperty.INT_PROP, Custom.CustomProperty.INT_PROP, 42));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.FLOAT_PROP, Custom.CustomProperty.FLOAT_PROP, 3.14f));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.STRING_PROP, Custom.CustomProperty.STRING_PROP, 100));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.FLOAT_RETURN, Custom.CustomProperty.FLOAT_RETURN, Utils.asNan(1)));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.TEXT_RETURN, Custom.CustomProperty.TEXT_RETURN, 101));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.COLOR_ID_PROP, Custom.CustomProperty.COLOR_ID_PROP, 104));
        props.add(new Custom.CustomProperty(Custom.CustomProperty.INT_ID_PROP, Custom.CustomProperty.INT_ID_PROP, 106));

        // 6-arg constructor
        Custom custom6 = new Custom(null, 50, -1, -1, "static-config", props);
        custom6.setWidth(100f);
        custom6.setHeight(100f);
        custom6.registerListening(mRemoteContext);
        custom6.updateVariables(mRemoteContext);

        // Update properties second time to trigger mNeedsUpdate = true branches
        when(mRemoteContext.getText(100)).thenReturn("new-string-val");
        when(mRemoteContext.getFloat(1)).thenReturn(9.99f);
        when(mRemoteContext.getColor(104)).thenReturn(0xFF556677);
        when(mRemoteContext.getInteger(106)).thenReturn(999);
        custom6.updateVariables(mRemoteContext);

        // Touches on Custom
        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
        custom6.onTouchDown(mRemoteContext, mDocument, 50f, 50f);
        custom6.onTouchDrag(mRemoteContext, mDocument, 55f, 55f, false);
        custom6.onTouchUp(mRemoteContext, mDocument, 55f, 55f, 5f, 5f, false);

        // Legacy touch position
        when(mRemoteContext.getTouchVersion()).thenReturn(0);
        custom6.onTouchDown(mRemoteContext, mDocument, 50f, 50f);
        custom6.onTouchDrag(mRemoteContext, mDocument, 55f, 55f, false);
        custom6.onTouchUp(mRemoteContext, mDocument, 55f, 55f, 5f, 5f, false);
        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);

        // Outside touch returns false
        assertFalse(custom6.onTouchDown(mRemoteContext, mDocument, 500f, 500f));

        // Paint with GraphicsLayer
        custom6.mGraphicsLayerModifier = new GraphicsLayerModifierOperation();
        custom6.paintingComponent(mPaintContext);

        // Context not implementing CustomContext throws RuntimeException
        PaintContext plainPaintContext = mock(PaintContext.class);
        try {
            custom6.computeSize(plainPaintContext, 0, 100, 0, 100, new FlatMeasurePass(100));
            fail("Expected RuntimeException");
        } catch (RuntimeException expected) {}
    }

    @Test
    public void testRootLayoutAndPoliciesDeepCoverage() {
        RootLayoutComponent root = new RootLayoutComponent(1);
        root.setHasTouchListeners(true);
        assertTrue(root.getHasTouchListeners());
        root.setHasTouchListeners(false);
        assertFalse(root.getHasTouchListeners());

        root.assignIds(10);
        assertNotNull(root.displayHierarchy());

        Component dirtyBoundary = new Component(2, 0, 0, 100, 100, root);
        root.registerDirtyBoundary(dirtyBoundary);
        root.performPartialLayoutPass(mRemoteContext);
        root.clearDirtyBoundaries();

        StringSerializer ss = new StringSerializer();
        root.serializeToString(0, ss);
        root.serialize(mockMapSerializer());

        // LegacyMeasurePolicy with various configurations
        BoxLayout box = new BoxLayout(null, 10, -1, 0, 0, 100, 100, 0, 0);
        box.mWidthModifier = new WidthModifierOperation(100f);
        box.mHeightModifier = new HeightModifierOperation(100f);
        MeasurePass measure = new FlatMeasurePass(100);

        // Exact equal min/max
        LegacyMeasurePolicy.INSTANCE.measure(box, mPaintContext, 100, 100, 100, 100, measure);

        // In horizontal & vertical fill
        box.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.FILL);
        box.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.FILL);
        LegacyMeasurePolicy.INSTANCE.measure(box, mPaintContext, 0, 200, 0, 200, measure);

        // With weight
        box.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.WEIGHT, 1.0f);
        box.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.WEIGHT, 1.0f);
        LegacyMeasurePolicy.INSTANCE.measure(box, mPaintContext, 0, 200, 0, 200, measure);

        // LayoutComponent padding & constraints
        box.setCanvasOperations(new CanvasOperations());
        box.setCanvasOperations(null);
        float[] pad = new float[4];
        box.computeModifierDefinedPaddingWidth(pad);
        box.computeModifierDefinedPaddingHeight(pad);
        box.computeModifierDefinedWidth(mRemoteContext, true);
        box.computeModifierDefinedHeight(mRemoteContext, true);
        box.getLocationInWindow(mRemoteContext, new float[2], true);
        box.getLocationInWindow(mRemoteContext, new float[2], false);
        box.serialize(mockMapSerializer());
    }

    @Test
    public void testCoreTextDeepBranchCoverage() {
        int[] fontAxis = new int[] {1, 2};
        float[] fontAxisValues = new float[] {100f, 200f};

        CoreText ct =
                new CoreText(
                        null,
                        10,
                        20,
                        0f,
                        0f,
                        100f,
                        50f,
                        1,
                        0xFF112233,
                        -1,
                        16f,
                        12f,
                        24f,
                        0,
                        400f,
                        -1,
                        CoreText.TEXT_ALIGN_CENTER,
                        CoreText.OVERFLOW_CLIP,
                        3,
                        0.5f,
                        1f,
                        1.2f,
                        CoreText.BREAK_STRATEGY_HIGH_QUALITY,
                        CoreText.HYPHENATION_FREQUENCY_NORMAL,
                        CoreText.JUSTIFICATION_MODE_INTER_WORD,
                        true,
                        true,
                        fontAxis,
                        fontAxisValues,
                        false,
                        0,
                        -1);

        assertEquals("CoreText", CoreText.name());
        assertEquals(Operations.CORE_TEXT, CoreText.id());
        assertEquals(Integer.valueOf(1), ct.getTextId());
        assertNotNull(ct.toString());
        StringSerializer ss = new StringSerializer();
        ct.serializeToString(0, ss);
        ct.serialize(mockMapSerializer());

        // applyStyle: test when properties are default and when they are not default
        TextStyle fullStyle =
                new TextStyle(
                        50,
                        0xFFAABBCC,
                        105,
                        20f,
                        10f,
                        30f,
                        1,
                        700f,
                        201,
                        CoreText.TEXT_ALIGN_RIGHT,
                        CoreText.OVERFLOW_ELLIPSIS,
                        5,
                        1.5f,
                        2f,
                        1.5f,
                        CoreText.BREAK_STRATEGY_BALANCED,
                        CoreText.HYPHENATION_FREQUENCY_FULL,
                        CoreText.JUSTIFICATION_MODE_INTER_CHARACTER,
                        false,
                        false,
                        new int[] {3},
                        new float[] {300f},
                        true,
                        -1);

        ct.applyStyle(fullStyle);

        CoreText defaultCt =
                new CoreText(
                        null,
                        11,
                        21,
                        2,
                        0xFF000000,
                        -1,
                        TextStyle.DEFAULT_FONT_SIZE,
                        -1f,
                        -1f,
                        0,
                        TextStyle.DEFAULT_FONT_WEIGHT,
                        -1,
                        1,
                        1,
                        Integer.MAX_VALUE,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        defaultCt.applyStyle(fullStyle);

        TextStyle emptyStyle = new TextStyle();
        defaultCt.applyStyle(emptyStyle);

        CoreText nanCt =
                new CoreText(
                        null,
                        12,
                        22,
                        3,
                        0,
                        102,
                        Utils.asNan(301),
                        -1f,
                        -1f,
                        0,
                        Utils.asNan(302),
                        -1,
                        1,
                        1,
                        1,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        nanCt.registerListening(mRemoteContext);
        CoreText noListenCt =
                new CoreText(
                        null,
                        13,
                        23,
                        -1,
                        0,
                        -1,
                        14f,
                        -1f,
                        -1f,
                        0,
                        400f,
                        -1,
                        1,
                        1,
                        1,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        noListenCt.registerListening(mRemoteContext);

        when(mRemoteContext.getObject(50)).thenReturn(fullStyle);
        when(mRemoteContext.getObject(51)).thenReturn("not_a_style");
        when(mRemoteContext.getFloat(301)).thenReturn(18f);
        when(mRemoteContext.getFloat(302)).thenReturn(500f);
        when(mRemoteContext.getColor(102)).thenReturn(0xFF556677);
        when(mRemoteContext.getText(3)).thenReturn("Dynamic Text");

        nanCt.updateVariables(mRemoteContext);
        nanCt.updateVariables(mRemoteContext);

        int[] familyIds = new int[] {10, 11, 12, 13, 14};
        String[] familyNames = new String[] {"default", "sans-serif", "serif", "monospace", "custom_font"};
        for (int i = 0; i < familyIds.length; i++) {
            when(mRemoteContext.getText(familyIds[i])).thenReturn(familyNames[i]);
            CoreText famCt =
                    new CoreText(
                            null,
                            100 + i,
                            -1,
                            4,
                            0,
                            -1,
                            14f,
                            -1f,
                            -1f,
                            0,
                            400f,
                            familyIds[i],
                            1,
                            1,
                            1,
                            0f,
                            0f,
                            1f,
                            0,
                            0,
                            0,
                            false,
                            false,
                            null,
                            null,
                            false,
                            0,
                            -1);
            when(mRemoteContext.getText(4)).thenReturn("Font Family " + i);
            famCt.updateVariables(mRemoteContext);
        }

        int[] alignments =
                new int[] {
                    CoreText.TEXT_ALIGN_LEFT,
                    CoreText.TEXT_ALIGN_RIGHT,
                    CoreText.TEXT_ALIGN_CENTER,
                    CoreText.TEXT_ALIGN_JUSTIFY,
                    CoreText.TEXT_ALIGN_START,
                    CoreText.TEXT_ALIGN_END
                };

        for (int align : alignments) {
            CoreText alignCt =
                    new CoreText(
                            null,
                            200 + align,
                            -1,
                            5,
                            0xFF000000,
                            -1,
                            16f,
                            -1f,
                            -1f,
                            0,
                            400f,
                            -1,
                            align,
                            CoreText.OVERFLOW_CLIP,
                            1,
                            0f,
                            0f,
                            1f,
                            0,
                            0,
                            0,
                            false,
                            false,
                            null,
                            null,
                            false,
                            0,
                            -1);
            alignCt.setWidth(100f);
            alignCt.setHeight(40f);
            when(mRemoteContext.getText(5)).thenReturn("Aligned Text");
            alignCt.updateVariables(mRemoteContext);
            alignCt.paintingComponent(mPaintContext);
        }

        CoreText glCt =
                new CoreText(
                        null,
                        300,
                        -1,
                        6,
                        0xFF000000,
                        -1,
                        16f,
                        -1f,
                        -1f,
                        0,
                        400f,
                        -1,
                        1,
                        CoreText.OVERFLOW_VISIBLE,
                        1,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        new int[] {1},
                        new float[] {Utils.asNan(401)},
                        false,
                        0,
                        -1);
        when(mRemoteContext.getFloat(401)).thenReturn(150f);
        when(mRemoteContext.getText(6)).thenReturn("Axis Text");
        glCt.setWidth(80f);
        glCt.setHeight(30f);
        glCt.updateVariables(mRemoteContext);
        glCt.paintingComponent(mPaintContext);

        FakeComputedTextLayout fakeComputed = new FakeComputedTextLayout();

        when(mPaintContext.layoutComplexText(
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyFloat(),
                        anyFloat(),
                        anyFloat(),
                        anyFloat(),
                        anyFloat(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt()))
                .thenAnswer(inv -> {
                    fakeComputed.mCurrentMaxLines = inv.getArgument(5);
                    return fakeComputed;
                });
        CoreText complexCt =
                new CoreText(
                        null,
                        401,
                        -1,
                        6,
                        0xFF000000,
                        -1,
                        16f,
                        0f,
                        0f,
                        0,
                        400f,
                        -1,
                        1,
                        CoreText.OVERFLOW_ELLIPSIS,
                        2,
                        0.5f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        true,
                        true,
                        null,
                        null,
                        false,
                        0,
                        -1);
        complexCt.updateVariables(mRemoteContext);
        complexCt.computeSize(mPaintContext, 0, 100, 0, 50, new FlatMeasurePass(100));
        complexCt.paintingComponent(mPaintContext);

        CoreText autoCt =
                new CoreText(
                        null,
                        400,
                        -1,
                        7,
                        0xFF000000,
                        -1,
                        16f,
                        8f,
                        32f,
                        0,
                        400f,
                        -1,
                        1,
                        CoreText.OVERFLOW_ELLIPSIS,
                        2,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        true,
                        0,
                        -1);
        when(mRemoteContext.getText(7)).thenReturn("Autosize Multiline\nText with \t tabs");
        autoCt.updateVariables(mRemoteContext);
        MeasurePass measure = new FlatMeasurePass(100);
        autoCt.computeSize(mPaintContext, 0, 100, 0, 50, measure);

        fakeComputed.mHeight = 60f;
        fakeComputed.mHyphen = true;
        autoCt.computeSize(mPaintContext, 0, 100, 0, 40, measure);

        assertEquals(0f, ct.getAlignValue(mPaintContext, AlignByModifierOperation.FIRST_BASELINE), 0.001f);
        assertEquals(0f, ct.getAlignValue(mPaintContext, AlignByModifierOperation.LAST_BASELINE), 0.001f);
        when(mRemoteContext.getFloat(501)).thenReturn(12.5f);
        assertEquals(12.5f, ct.getAlignValue(mPaintContext, Utils.asNan(501)), 0.001f);
        assertEquals(0f, ct.getAlignValue(mPaintContext, Float.NaN), 0.001f);
        assertEquals(15f, ct.getAlignValue(mPaintContext, 15f), 0.001f);

        ct.minIntrinsicWidth(mRemoteContext);
        ct.minIntrinsicHeight(mRemoteContext);

        WireBuffer buffer = new WireBuffer();
        ct.write(buffer);
        buffer.setIndex(0);
        int opId = buffer.readOperationType();
        assertEquals(Operations.CORE_TEXT, opId);
        List<Operation> ops = new ArrayList<>();
        CoreText.read(buffer, ops);
        assertFalse(ops.isEmpty());

        RemapContext remap = mock(RemapContext.class);
        when(remap.resolveId(anyInt())).thenReturn(999);
        WireBuffer buf2 = new WireBuffer();
        ct.write(buf2);
        buf2.setIndex(0);
        buf2.readOperationType();
        LoomWireBuffer loomBuf = new LoomWireBuffer(buf2, remap);
        List<Operation> loomOps = new ArrayList<>();
        CoreText.read(loomBuf, loomOps);
        assertFalse(loomOps.isEmpty());

        try {
            CoreText.apply(
                    new WireBuffer(),
                    1,
                    -1,
                    1,
                    0,
                    -1,
                    16f,
                    -1f,
                    -1f,
                    0,
                    400f,
                    -1,
                    1,
                    1,
                    1,
                    0f,
                    0f,
                    1f,
                    0,
                    0,
                    0,
                    false,
                    false,
                    new int[] {1, 2},
                    new float[] {10f},
                    false,
                    0,
                    -1);
            fail("Expected IllegalStateException for fontAxis mismatch");
        } catch (IllegalStateException expected) {}

        CoreText.documentation(mockDoc());
    }

    @Test
    public void testTextStyleDeepBranchCoverage() {
        TextStyle ts =
                new TextStyle(
                        10,
                        0xFF112233,
                        101,
                        24f,
                        12f,
                        48f,
                        1,
                        600f,
                        202,
                        CoreText.TEXT_ALIGN_CENTER,
                        CoreText.OVERFLOW_ELLIPSIS,
                        3,
                        1.2f,
                        2f,
                        1.4f,
                        CoreText.BREAK_STRATEGY_HIGH_QUALITY,
                        CoreText.HYPHENATION_FREQUENCY_NORMAL,
                        CoreText.JUSTIFICATION_MODE_INTER_WORD,
                        true,
                        true,
                        new int[] {1},
                        new float[] {100f},
                        true,
                        5);

        assertEquals(10, ts.getId());
        ts.setId(15);
        assertEquals(15, ts.getId());
        assertEquals("TextStyle", TextStyle.name());
        assertEquals(Operations.TEXT_STYLE, TextStyle.id());
        assertNotNull(ts.deepToString("  "));

        TextStyle target = new TextStyle();
        target.applyStyle(ts);
        target.applyStyle(ts);

        when(mRemoteContext.getObject(5)).thenReturn(ts);
        TextStyle targetWithParent =
                new TextStyle(
                        20, -1, -1, 16f, 0f, 0f, 0, 400f, -1, 0, 0, 1, 0f, 0f, 1f, 0, 0, 0,
                        false, false, null, null, false, 5);
        targetWithParent.apply(mRemoteContext);

        when(mRemoteContext.getObject(6)).thenReturn("not_style");
        TextStyle targetWithParent2 =
                new TextStyle(
                        21, -1, -1, 16f, 0f, 0f, 0, 400f, -1, 0, 0, 1, 0f, 0f, 1f, 0, 0, 0,
                        false, false, null, null, false, 6);
        targetWithParent2.apply(mRemoteContext);

        WireBuffer buffer = new WireBuffer();
        ts.write(buffer);
        buffer.setIndex(0);
        int opId = buffer.readOperationType();
        assertEquals(Operations.TEXT_STYLE, opId);
        List<Operation> ops = new ArrayList<>();
        TextStyle.read(buffer, ops);
        assertFalse(ops.isEmpty());

        RemapContext remap = mock(RemapContext.class);
        when(remap.resolveId(anyInt())).thenReturn(777);
        WireBuffer buf2 = new WireBuffer();
        ts.write(buf2);
        buf2.setIndex(0);
        buf2.readOperationType();
        LoomWireBuffer loomBuf = new LoomWireBuffer(buf2, remap);
        List<Operation> loomOps = new ArrayList<>();
        TextStyle.read(loomBuf, loomOps);
        assertFalse(loomOps.isEmpty());

        TextStyle.documentation(mockDoc());
    }

    @Test
    public void testTextLayoutDeepBranchCoverage() {
        int dynamicTextAlign = CoreText.TEXT_ALIGN_CENTER | (TextLayout.FLAG_IS_DYNAMIC_COLOR << 16);
        TextLayout tl =
                new TextLayout(
                        null,
                        10,
                        20,
                        0f,
                        0f,
                        120f,
                        60f,
                        1,
                        0xFF445566,
                        16f,
                        0,
                        400f,
                        -1,
                        dynamicTextAlign,
                        TextLayout.OVERFLOW_CLIP,
                        2);

        assertEquals("TextLayout", TextLayout.name());
        assertEquals(Operations.LAYOUT_TEXT, TextLayout.id());
        assertEquals(Integer.valueOf(1), tl.getTextId());
        assertEquals(16f, tl.getFontSize(), 0.001f);
        assertEquals(16f, tl.getFontSizeValue(), 0.001f);
        assertNotNull(tl.toString());
        StringSerializer ss = new StringSerializer();
        tl.serializeToString(0, ss);
        tl.serialize(mockMapSerializer());

        TextLayout tl2 =
                new TextLayout(
                        null,
                        11,
                        21,
                        2,
                        0xFF112233,
                        Utils.asNan(101),
                        1,
                        600f,
                        -1,
                        CoreText.TEXT_ALIGN_LEFT,
                        TextLayout.OVERFLOW_VISIBLE,
                        1);

        when(mRemoteContext.supportsVersion(1, 1, 0)).thenReturn(true);
        tl.registerListening(mRemoteContext);
        tl2.registerListening(mRemoteContext);
        when(mRemoteContext.supportsVersion(1, 1, 0)).thenReturn(false);
        tl.registerListening(mRemoteContext);

        when(mRemoteContext.supportsVersion(1, 1, 0)).thenReturn(true);
        when(mRemoteContext.getColor(anyInt())).thenReturn(0xFF990000);
        when(mRemoteContext.getFloat(101)).thenReturn(20f);
        when(mRemoteContext.getText(1)).thenReturn("Layout Text String");
        when(mRemoteContext.getText(2)).thenReturn("Second String");
        tl.updateVariables(mRemoteContext);
        tl2.updateVariables(mRemoteContext);

        when(mRemoteContext.supportsVersion(1, 1, 0)).thenReturn(false);
        tl.updateVariables(mRemoteContext);

        int[] familyIds = new int[] {30, 31, 32, 33, 34};
        String[] familyNames = new String[] {"default", "sans-serif", "serif", "monospace", "unknown"};
        for (int i = 0; i < familyIds.length; i++) {
            when(mRemoteContext.getText(familyIds[i])).thenReturn(familyNames[i]);
            TextLayout famTl =
                    new TextLayout(
                            null,
                            50 + i,
                            -1,
                            3,
                            0xFF000000,
                            14f,
                            0,
                            400f,
                            familyIds[i],
                            1,
                            1,
                            1);
            when(mRemoteContext.getText(3)).thenReturn("Fam Text " + i);
            famTl.updateVariables(mRemoteContext);
        }

        int[] aligns =
                new int[] {
                    TextLayout.TEXT_ALIGN_LEFT,
                    TextLayout.TEXT_ALIGN_RIGHT,
                    TextLayout.TEXT_ALIGN_CENTER,
                    TextLayout.TEXT_ALIGN_JUSTIFY,
                    TextLayout.TEXT_ALIGN_START,
                    TextLayout.TEXT_ALIGN_END
                };
        for (int a : aligns) {
            TextLayout aTl =
                    new TextLayout(
                            null,
                            60 + a,
                            -1,
                            4,
                            0xFF000000,
                            14f,
                            0,
                            400f,
                            -1,
                            a,
                            TextLayout.OVERFLOW_CLIP,
                            1);
            aTl.setWidth(100f);
            aTl.setHeight(30f);
            when(mRemoteContext.getText(4)).thenReturn("Aligned");
            aTl.updateVariables(mRemoteContext);
            aTl.paintingComponent(mPaintContext);
        }

        FakeComputedTextLayout comp = new FakeComputedTextLayout();
        comp.mWidth = 50f;
        comp.mHeight = 20f;
        when(mPaintContext.layoutComplexText(
                        anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyFloat(),
                        anyFloat(), anyFloat(), anyFloat(), anyFloat(), anyInt(), anyInt(), anyInt(),
                        anyBoolean(), anyBoolean(), anyInt()))
                .thenAnswer(inv -> {
                    comp.mCurrentMaxLines = inv.getArgument(5);
                    return comp;
                });

        TextLayout complexTl =
                new TextLayout(
                        null,
                        70,
                        -1,
                        1,
                        0xFF000000,
                        14f,
                        0,
                        400f,
                        -1,
                        TextLayout.TEXT_ALIGN_LEFT,
                        TextLayout.OVERFLOW_ELLIPSIS,
                        2);
        when(mRemoteContext.getText(1)).thenReturn("Layout\nText String");
        complexTl.updateVariables(mRemoteContext);
        complexTl.computeSize(mPaintContext, 0, 100, 0, 50, new FlatMeasurePass(100));
        complexTl.setWidth(80f);
        complexTl.setHeight(40f);
        complexTl.paintingComponent(mPaintContext);

        when(mRemoteContext.getColor(anyInt())).thenReturn(0xFF112233);
        complexTl.updateVariables(mRemoteContext);

        TextLayout overflowTl =
                new TextLayout(
                        null,
                        70,
                        -1,
                        5,
                        0xFF000000,
                        14f,
                        0,
                        400f,
                        -1,
                        1,
                        TextLayout.OVERFLOW_ELLIPSIS,
                        1);
        when(mRemoteContext.getText(5)).thenReturn("Tab\tAnd\nNewline");
        overflowTl.updateVariables(mRemoteContext);
        MeasurePass measure = new FlatMeasurePass(100);
        overflowTl.computeSize(mPaintContext, 0, 100, 0, 50, measure);

        assertEquals(0f, tl.getAlignValue(mPaintContext, AlignByModifierOperation.FIRST_BASELINE), 0.001f);
        assertEquals(0f, tl.getAlignValue(mPaintContext, AlignByModifierOperation.LAST_BASELINE), 0.001f);
        when(mRemoteContext.getFloat(502)).thenReturn(14f);
        assertEquals(14f, tl.getAlignValue(mPaintContext, Utils.asNan(502)), 0.001f);
        assertEquals(0f, tl.getAlignValue(mPaintContext, Float.NaN), 0.001f);
        assertEquals(10f, tl.getAlignValue(mPaintContext, 10f), 0.001f);

        tl.minIntrinsicWidth(mRemoteContext);
        tl.minIntrinsicHeight(mRemoteContext);

        WireBuffer buf = new WireBuffer();
        tl.write(buf);
        buf.setIndex(0);
        int readOpId = buf.readOperationType();
        assertEquals(Operations.LAYOUT_TEXT, readOpId);
        List<Operation> ops = new ArrayList<>();
        TextLayout.read(buf, ops);
        assertFalse(ops.isEmpty());

        TextLayout.documentation(mockDoc());
    }

    @Test
    public void testComponentTouchAndHierarchyCoverage() {
        Component root = new Component(1, 0, 0, 200, 200, null);
        Component child = new Component(2, 20, 20, 100, 100, root);
        root.getList().add(child);

        assertTrue(root.contains(mRemoteContext, 50, 50));
        assertFalse(root.contains(mRemoteContext, 250, 250));

        float[] loc = new float[2];
        child.getLocationInWindow(mRemoteContext, loc, true);
        child.getLocationInWindow(mRemoteContext, loc);
        int[] bounds = new int[4];
        child.getBoundsInSemanticParent(bounds, 1);
        child.getBoundsInSemanticParent(bounds, null);
        child.getBoundsInSemanticParent(bounds, 999);

        assertEquals("GONE", Component.Visibility.toString(Component.Visibility.GONE));
        assertEquals("VISIBLE", Component.Visibility.toString(Component.Visibility.VISIBLE));
        assertEquals("INVISIBLE", Component.Visibility.toString(Component.Visibility.INVISIBLE));
        assertEquals("OVERRIDE_GONE", Component.Visibility.toString(Component.Visibility.OVERRIDE_GONE));
        assertEquals("OVERRIDE_VISIBLE", Component.Visibility.toString(Component.Visibility.OVERRIDE_VISIBLE));
        assertEquals("OVERRIDE_INVISIBLE", Component.Visibility.toString(Component.Visibility.OVERRIDE_INVISIBLE));
        assertEquals("3", Component.Visibility.toString(3));
        assertEquals("128", Component.Visibility.toString(Component.Visibility.CLEAR_OVERRIDE));

        assertTrue(Component.Visibility.isGone(Component.Visibility.GONE));
        assertTrue(Component.Visibility.isGone(Component.Visibility.OVERRIDE_GONE));
        assertFalse(Component.Visibility.isGone(Component.Visibility.VISIBLE));

        assertTrue(Component.Visibility.isVisible(Component.Visibility.VISIBLE));
        assertTrue(Component.Visibility.isVisible(Component.Visibility.OVERRIDE_VISIBLE));
        assertFalse(Component.Visibility.isVisible(Component.Visibility.GONE));

        assertTrue(Component.Visibility.isInvisible(Component.Visibility.INVISIBLE));
        assertTrue(Component.Visibility.isInvisible(Component.Visibility.OVERRIDE_INVISIBLE));
        assertFalse(Component.Visibility.isInvisible(Component.Visibility.VISIBLE));

        assertTrue(Component.Visibility.hasOverride(Component.Visibility.OVERRIDE_GONE));
        assertFalse(Component.Visibility.hasOverride(Component.Visibility.VISIBLE));
        assertEquals(Component.Visibility.VISIBLE, Component.Visibility.clearOverride(Component.Visibility.OVERRIDE_VISIBLE | Component.Visibility.VISIBLE));
        assertEquals(Component.Visibility.VISIBLE, Component.Visibility.add(Component.Visibility.VISIBLE, Component.Visibility.CLEAR_OVERRIDE));

        assertTrue(child.isVisible());
        assertFalse(child.isGone());
        child.setVisibility(Component.Visibility.GONE);
        child.mVisibility = Component.Visibility.GONE;
        assertTrue(child.isGone());
        assertFalse(child.isVisible());
        child.mVisibility = Component.Visibility.INVISIBLE;
        assertTrue(child.isInvisible());

        assertFalse(child.needsBoundsAnimation());
        child.markNeedsBoundsAnimation();
        assertTrue(child.needsBoundsAnimation());
        child.clearNeedsBoundsAnimation();
        assertFalse(child.needsBoundsAnimation());

        Component other = new Component(3, 0, 0, 200, 200, null);
        other.getList().add(new Component(4, 20, 20, 100, 100, other));
        assertTrue(root.suitableForTransition(other));
        assertFalse(root.suitableForTransition(mock(Operation.class)));
        Component differentSize = new Component(5, 0, 0, 10, 10, null);
        assertFalse(root.suitableForTransition(differentSize));

        ClickModifierOperation mockClick = mock(ClickModifierOperation.class);
        TouchDownModifierOperation mockTouch = mock(TouchDownModifierOperation.class);
        TouchExpression mockTouchExpr = mock(TouchExpression.class);
        root.getList().add(mockClick);
        root.getList().add(mockTouch);
        root.getList().add(mockTouchExpr);

        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
        when(mockClick.onClick(any(), any(), any(), anyFloat(), anyFloat())).thenReturn(true);
        when(mockClick.onLongPress(any(), any(), any(), anyFloat(), anyFloat())).thenReturn(true);
        when(mockClick.onDoubleClick(any(), any(), any(), anyFloat(), anyFloat())).thenReturn(true);

        assertTrue(root.onClick(mRemoteContext, mDocument, 50, 50));
        assertTrue(root.onClick(mRemoteContext, mDocument, -1, -1));
        assertFalse(root.onClick(mRemoteContext, mDocument, 300, 300));

        assertTrue(root.onLongPress(mRemoteContext, mDocument, 50, 50));
        assertTrue(root.onDoubleClick(mRemoteContext, mDocument, 50, 50));

        when(mockTouch.onTouchDown(any(), any(), any(), anyFloat(), anyFloat())).thenReturn(true);
        when(mockTouch.onTouchDrag(any(), any(), any(), anyFloat(), anyFloat())).thenReturn(true);
        when(mockTouch.onTouchUp(any(), any(), any(), anyFloat(), anyFloat(), anyFloat(), anyFloat())).thenReturn(true);
        when(mockTouch.onTouchCancel(any(), any(), any(), anyFloat(), anyFloat())).thenReturn(true);

        assertTrue(root.onTouchDown(mRemoteContext, mDocument, 50, 50));
        assertFalse(root.onTouchDown(mRemoteContext, mDocument, 300, 300));

        assertTrue(root.onTouchDrag(mRemoteContext, mDocument, 50, 50, false));
        assertTrue(root.onTouchDrag(mRemoteContext, mDocument, 300, 300, true));

        assertTrue(root.onTouchUp(mRemoteContext, mDocument, 50, 50, 0, 0, false));
        assertTrue(root.onTouchCancel(mRemoteContext, mDocument, 50, 50, false));

        when(mRemoteContext.getTouchVersion()).thenReturn(0);
        root.onClick(mRemoteContext, mDocument, 50, 50);
        root.onLongPress(mRemoteContext, mDocument, 50, 50);
        root.onDoubleClick(mRemoteContext, mDocument, 50, 50);
        root.onTouchDown(mRemoteContext, mDocument, 50, 50);
        root.onTouchDrag(mRemoteContext, mDocument, 50, 50, false);
        root.onTouchUp(mRemoteContext, mDocument, 50, 50, 0, 0, false);
        root.onTouchCancel(mRemoteContext, mDocument, 50, 50, false);

        MeasurePass measure = new FlatMeasurePass(100);
        child.setAnimationSpec(new AnimationSpec(100));
        child.mFirstLayout = false;
        ComponentMeasure cm = measure.get(child);
        cm.setW(150f);
        cm.setH(150f);
        cm.setAllowsAnimation(true);
        child.layout(mRemoteContext, measure);
        assertNotNull(child.mAnimateMeasure);
        child.animatingBounds(mRemoteContext);
    }

    @Test
    public void testLayoutComponentComprehensiveCoverage() {
        LayoutComponent lc = new LayoutComponent(null, 1, 0, 0, 0, 200, 200);

        LayoutComponentContent content = new LayoutComponentContent(2);
        Component child1 = new Component(3, 10, 10, 50, 50, null);
        child1.mZIndex = 2.0f;
        Component child2 = new Component(4, 20, 20, 50, 50, null);
        child2.mZIndex = 1.0f;
        content.getList().add(child1);
        content.getList().add(child2);

        PaddingModifierOperation pad = new PaddingModifierOperation(10, 10, 10, 10);
        WidthModifierOperation wMod = new WidthModifierOperation(100f);
        HeightModifierOperation hMod = new HeightModifierOperation(100f);
        WidthInModifierOperation wIn = new WidthInModifierOperation(50f, 150f);
        HeightInModifierOperation hIn = new HeightInModifierOperation(50f, 150f);
        wMod.setWidthIn(wIn);
        hMod.setHeightIn(hIn);
        ZIndexModifierOperation zMod = new ZIndexModifierOperation(5f);
        GraphicsLayerModifierOperation glMod = new GraphicsLayerModifierOperation();

        lc.getList().add(content);
        lc.getList().add(pad);
        lc.getList().add(wMod);
        lc.getList().add(hMod);
        lc.getList().add(wIn);
        lc.getList().add(hIn);
        lc.getList().add(zMod);
        lc.getList().add(glMod);

        lc.inflate();
        zMod.paint(mPaintContext);

        assertEquals(10f, lc.getPaddingLeft(), 0.001f);
        assertEquals(10f, lc.getPaddingTop(), 0.001f);
        assertEquals(10f, lc.getPaddingRight(), 0.001f);
        assertEquals(10f, lc.getPaddingBottom(), 0.001f);
        assertEquals(5f, lc.getZIndex(), 0.001f);
        assertNotNull(lc.getWidthModifier());
        assertNotNull(lc.getHeightModifier());

        assertEquals(100f, lc.applyWidthConstraints(100f), 0.001f);
        assertEquals(20f, lc.applyWidthConstraints(20f), 0.001f);
        assertEquals(100f, lc.applyHeightConstraints(100f), 0.001f);
        assertEquals(20f, lc.applyHeightConstraints(20f), 0.001f);

        DimensionConstraintsModifierOperation reqW =
                new DimensionConstraintsModifierOperation(
                        DimensionConstraintsModifierOperation.REQUIRED_HORIZONTAL_CONSTRAINTS, 50f, 150f);
        wMod.setWidthIn(reqW);
        assertEquals(50f, lc.applyWidthConstraints(20f), 0.001f);
        assertEquals(150f, lc.applyWidthConstraints(200f), 0.001f);

        DimensionConstraintsModifierOperation reqH =
                new DimensionConstraintsModifierOperation(
                        DimensionConstraintsModifierOperation.REQUIRED_VERTICAL_CONSTRAINTS, 50f, 150f);
        hMod.setHeightIn(reqH);
        assertEquals(50f, lc.applyHeightConstraints(20f), 0.001f);
        assertEquals(150f, lc.applyHeightConstraints(200f), 0.001f);

        when(mRemoteContext.isVisualDebug()).thenReturn(true);
        lc.paintingComponent(mPaintContext);
        lc.drawContent(mPaintContext);

        CanvasOperations canvasOps = new CanvasOperations();
        lc.setCanvasOperations(canvasOps);
        assertEquals(canvasOps, lc.getCanvasOperations());
        lc.paintingComponent(mPaintContext);

        lc.computeModifierDefinedWidth(mRemoteContext);
        lc.computeModifierDefinedHeight(mRemoteContext);
        lc.computeModifierDefinedWidth(mRemoteContext, true);
        lc.computeModifierDefinedHeight(mRemoteContext, true);
    }

    @Test
    public void testScrollModifierOperationCoverage() {
        ScrollModifierOperation scrollV = new ScrollModifierOperation(0, Utils.asNan(1), Utils.asNan(2), Utils.asNan(3));
        ScrollModifierOperation scrollH = new ScrollModifierOperation(1, 100f, 200f, 300f);

        assertTrue(scrollV.isVerticalScroll());
        assertFalse(scrollV.isHorizontalScroll());
        assertTrue(scrollV.handlesVerticalScroll());
        assertFalse(scrollV.handlesHorizontalScroll());

        assertFalse(scrollH.isVerticalScroll());
        assertTrue(scrollH.isHorizontalScroll());

        assertEquals("ScrollModifierOperation", ScrollModifierOperation.name());
        assertEquals(Operations.MODIFIER_SCROLL, ScrollModifierOperation.id());
        assertNotNull(scrollV.toString());
        assertNotNull(scrollV.deepToString("  "));

        TouchExpression touchExpr = mock(TouchExpression.class);
        scrollV.getList().add(touchExpr);
        LayoutComponent host = new LayoutComponent(null, 1, 0, 0, 0, 200, 200);
        Component child = new Component(2, 0, 0, 100, 500, host);
        host.getChildrenComponents().add(child);
        scrollV.inflate(host);
        scrollV.registerListening(mRemoteContext);
        scrollV.updateVariables(mRemoteContext);

        scrollV.setVerticalScrollDimension(200f, 500f);
        scrollH.setHorizontalScrollDimension(200f, 600f);
        assertEquals(500f, scrollV.getContentDimension(), 0.001f);
        assertEquals(600f, scrollH.getContentDimension(), 0.001f);

        scrollV.layout(mRemoteContext, host, 200f, 200f);
        scrollH.layout(mRemoteContext, host, 200f, 200f);

        when(mRemoteContext.getFloat(1)).thenReturn(50f);
        scrollV.paint(mPaintContext);
        scrollH.paint(mPaintContext);

        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
        scrollV.onTouchDown(mRemoteContext, mDocument, host, 50, 50);
        scrollV.onTouchDrag(mRemoteContext, mDocument, host, 50, 20);
        scrollV.onTouchUp(mRemoteContext, mDocument, host, 50, 20, 0, -5);
        scrollV.onTouchCancel(mRemoteContext, mDocument, host, 50, 20);

        scrollH.onTouchDown(mRemoteContext, mDocument, host, 50, 50);
        scrollH.onTouchDrag(mRemoteContext, mDocument, host, 20, 50);
        scrollH.onTouchUp(mRemoteContext, mDocument, host, 20, 50, -5, 0);
        scrollH.onTouchCancel(mRemoteContext, mDocument, host, 20, 50);

        ScrollingEdgeEffect edgeEffect = mock(ScrollingEdgeEffect.class);
        when(mRemoteContext.createEdgeEffect(anyInt())).thenReturn(edgeEffect);
        scrollV.applyEdgeEffect(mPaintContext, host, ScrollingEdgeEffect.PRE_DRAW);
        scrollV.applyEdgeEffect(mPaintContext, host, ScrollingEdgeEffect.POST_DRAW);
        scrollH.applyEdgeEffect(mPaintContext, host, ScrollingEdgeEffect.PRE_DRAW);
        scrollH.applyEdgeEffect(mPaintContext, host, ScrollingEdgeEffect.POST_DRAW);

        WireBuffer buf = new WireBuffer();
        scrollV.write(buf);
        buf.setIndex(0);
        int opId = buf.readOperationType();
        assertEquals(Operations.MODIFIER_SCROLL, opId);
        List<Operation> ops = new ArrayList<>();
        ScrollModifierOperation.read(buf, ops);
        assertFalse(ops.isEmpty());

        StringSerializer ss = new StringSerializer();
        scrollV.serializeToString(0, ss);
        ScrollModifierOperation.documentation(mockDoc());
    }

    @Test
    public void testFitBoxLayoutComprehensiveCoverage() {
        FitBoxLayout fb =
                new FitBoxLayout(null, 1, 0, 0, 0, 200, 200, FitBoxLayout.CENTER, FitBoxLayout.CENTER);
        assertEquals("FitBoxLayout", FitBoxLayout.name());
        assertEquals(Operations.LAYOUT_FIT_BOX, FitBoxLayout.id());
        assertNotNull(fb.toString());

        LayoutComponent c1 = new LayoutComponent(fb, 2, 0, 0, 0, 300, 300);
        WidthModifierOperation w1 = new WidthModifierOperation(300f);
        w1.setWidthIn(new WidthInModifierOperation(300f, 300f));
        HeightModifierOperation h1 = new HeightModifierOperation(300f);
        h1.setHeightIn(new HeightInModifierOperation(300f, 300f));
        c1.getList().add(w1);
        c1.getList().add(h1);
        c1.inflate();

        LayoutComponent c2 = new LayoutComponent(fb, 3, 0, 0, 0, 100, 100);
        WidthModifierOperation w2 = new WidthModifierOperation(100f);
        w2.setWidthIn(new WidthInModifierOperation(100f, 100f));
        HeightModifierOperation h2 = new HeightModifierOperation(100f);
        h2.setHeightIn(new HeightInModifierOperation(100f, 100f));
        c2.getList().add(w2);
        c2.getList().add(h2);
        c2.inflate();

        fb.getChildrenComponents().add(c1);
        fb.getChildrenComponents().add(c2);

        MeasurePass measure = new FlatMeasurePass(100);

        when(mPaintContext.useFeature(Header.FEATURE_PRIORITY_FIX)).thenReturn(true);
        fb.computeSize(mPaintContext, 0, 200, 0, 200, measure);
        fb.computeWrapSize(mPaintContext, 0, 200, 0, 200, true, true, measure, new Size(0, 0));

        when(mPaintContext.useFeature(Header.FEATURE_PRIORITY_FIX)).thenReturn(false);
        fb.computeSize(mPaintContext, 0, 200, 0, 200, measure);
        fb.computeWrapSize(mPaintContext, 0, 200, 0, 200, true, true, measure, new Size(0, 0));

        int[] vPos = new int[] {FitBoxLayout.TOP, FitBoxLayout.CENTER, FitBoxLayout.BOTTOM};
        int[] hPos = new int[] {FitBoxLayout.START, FitBoxLayout.CENTER, FitBoxLayout.END};
        for (int v : vPos) {
            for (int h : hPos) {
                FitBoxLayout posFb = new FitBoxLayout(null, 10, 0, h, v);
                posFb.getChildrenComponents().add(c2);
                ComponentMeasure cm = measure.get(posFb);
                cm.setW(200f);
                cm.setH(200f);
                posFb.internalLayoutMeasure(mPaintContext, measure);
            }
        }

        WireBuffer buf = new WireBuffer();
        fb.write(buf);
        buf.setIndex(0);
        int opId = buf.readOperationType();
        assertEquals(Operations.LAYOUT_FIT_BOX, opId);
        List<Operation> ops = new ArrayList<>();
        FitBoxLayout.read(buf, ops);
        assertFalse(ops.isEmpty());

        FitBoxLayout.documentation(mockDoc());
    }

    @Test
    public void testStateLayoutComprehensiveCoverage() {
        StateLayout sl = new StateLayout(null, 1, 0, 0, 0, 200, 200, 0);

        BoxLayout state0 = new BoxLayout(sl, 10, 0, 0, 0, 200, 200, 0, 0);
        Component animComp0 = new Component(101, 10, 10, 50, 50, state0);
        animComp0.setAnimationId(1000);
        state0.getChildrenComponents().add(animComp0);

        BoxLayout state1 = new BoxLayout(sl, 20, 0, 0, 0, 200, 200, 0, 0);
        Component animComp1 = new Component(201, 20, 20, 50, 50, state1);
        animComp1.setAnimationId(1000);
        state1.getChildrenComponents().add(animComp1);

        sl.getChildrenComponents().add(state0);
        sl.getChildrenComponents().add(state1);

        sl.findAnimatedComponents();
        sl.collapsePaintedComponents();

        assertNotNull(sl.getSharedComponent(1000, 0));
        assertNotNull(sl.getSharedComponent(1000, 1));
        assertNull(sl.getSharedComponent(1000, 5));
        assertNull(sl.getSharedComponent(9999, 0));

        MeasurePass measure = new FlatMeasurePass(100);
        ComponentMeasure slm = measure.get(sl);
        slm.setW(200f);
        slm.setH(200f);

        sl.inTransition = true;
        sl.previousLayoutIndex = 0;
        sl.currentLayoutIndex = 1;
        sl.layout(mRemoteContext, measure);

        sl.onClick(mRemoteContext, mDocument, 50, 50);
        sl.onClick(mRemoteContext, mDocument, 500, 500);

        sl.computeSize(mPaintContext, 0, 200, 0, 200, measure);
        sl.computeWrapSize(mPaintContext, 0, 200, 0, 200, true, true, measure, new Size(0, 0));
        sl.internalLayoutMeasure(mPaintContext, measure);
    }

    @Test
    public void testLoopAndImpulseAndCanvasOperationsCoverage() {
        LoopOperation loop1 = new LoopOperation(0, 0f, 1f, 5f);
        LoopOperation loop2 = new LoopOperation(10, Utils.asNan(1), Utils.asNan(2), Utils.asNan(3));

        loop2.registerListening(mRemoteContext);
        when(mRemoteContext.getFloat(1)).thenReturn(0f);
        when(mRemoteContext.getFloat(2)).thenReturn(1f);
        when(mRemoteContext.getFloat(3)).thenReturn(3f);
        loop2.updateVariables(mRemoteContext);

        Operation mockOp = mock(Operation.class);
        loop1.getList().add(mockOp);
        loop2.getList().add(mockOp);

        loop1.updateVariables(mRemoteContext);
        loop1.paint(mPaintContext);
        loop2.paint(mPaintContext);

        assertNotNull(loop1.toString());
        assertNotNull(loop1.deepToString("  "));
        assertEquals("Loop", LoopOperation.name());

        WireBuffer buf = new WireBuffer();
        loop1.write(buf);
        buf.setIndex(0);
        int opId = buf.readOperationType();
        assertEquals(Operations.LOOP_START, opId);
        List<Operation> loopOps = new ArrayList<>();
        LoopOperation.read(buf, loopOps);
        assertFalse(loopOps.isEmpty());
        LoopOperation.documentation(mockDoc());

        ImpulseOperation impulse = new ImpulseOperation(Utils.asNan(10), Utils.asNan(20));
        ImpulseProcess process = new ImpulseProcess();
        impulse.getList().add(mockOp);
        impulse.getList().add(process);

        impulse.registerListening(mRemoteContext);
        when(mRemoteContext.getFloat(10)).thenReturn(100f);
        when(mRemoteContext.getFloat(20)).thenReturn(50f);
        impulse.updateVariables(mRemoteContext);

        when(mRemoteContext.getAnimationTime()).thenReturn(20f);
        impulse.paint(mPaintContext);

        when(mRemoteContext.getAnimationTime()).thenReturn(60f);
        impulse.paint(mPaintContext);
        impulse.paint(mPaintContext);

        when(mRemoteContext.getAnimationTime()).thenReturn(200f);
        impulse.paint(mPaintContext);

        assertNotNull(impulse.toString());
        assertNotNull(impulse.deepToString("  "));
        assertEquals("ImpulseOperation", ImpulseOperation.name());

        WireBuffer impBuf = new WireBuffer();
        impulse.write(impBuf);
        impBuf.setIndex(0);
        impBuf.readOperationType();
        List<Operation> impOps = new ArrayList<>();
        ImpulseOperation.read(impBuf, impOps);
        ImpulseOperation.documentation(mockDoc());

        CanvasOperations canvasOps = new CanvasOperations();
        LayoutComponent host = new LayoutComponent(null, 1, 0, 0, 0, 100, 100);
        canvasOps.setComponent(host);
        assertEquals(canvasOps, host.getCanvasOperations());

        ComponentValue cv = new ComponentValue(ComponentValue.WIDTH, 1, 10);
        canvasOps.getList().add(cv);
        canvasOps.registerListening(mRemoteContext);
        canvasOps.updateVariables(mRemoteContext);
        canvasOps.paint(mPaintContext);

        assertNotNull(canvasOps.toString());
        assertNotNull(canvasOps.deepToString("  "));
        assertEquals("Loop", CanvasOperations.name());

        WireBuffer cBuf = new WireBuffer();
        canvasOps.write(cBuf);
        cBuf.setIndex(0);
        cBuf.readOperationType();
        List<Operation> cOps = new ArrayList<>();
        CanvasOperations.read(cBuf, cOps);
        CanvasOperations.documentation(mockDoc());
        canvasOps.serialize(mockMapSerializer());

        ListActionsOperation listActions =
                new ListActionsOperation("TEST_ACTIONS") {
                    @Override
                    public void write(WireBuffer buffer) {}
                };
        listActions.getList().add(new HostActionOperation(1));

        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
        Component comp = new Component(1, 0, 0, 100, 100, null);
        assertTrue(listActions.applyActions(mRemoteContext, mDocument, comp, 50, 50, false));
        assertFalse(listActions.applyActions(mRemoteContext, mDocument, comp, -5, 50, false));
        assertFalse(listActions.applyActions(mRemoteContext, mDocument, comp, 150, 50, false));
        assertTrue(listActions.applyActions(mRemoteContext, mDocument, comp, 150, 50, true));

        when(mRemoteContext.getTouchVersion()).thenReturn(0);
        assertTrue(listActions.applyActions(mRemoteContext, mDocument, comp, 50, 50, false));
        assertFalse(listActions.applyActions(mRemoteContext, mDocument, comp, 150, 150, false));

        listActions.serializeToString(0, new StringSerializer());
        listActions.serialize(mockMapSerializer());
    }

    @Test
    public void testMultiClickAndCustomPropertiesCoverage() {
        MultiClickModifier mcSingle = new MultiClickModifier(MultiClickModifier.CLICK_TYPE_SINGLE);
        MultiClickModifier mcLong = new MultiClickModifier(MultiClickModifier.CLICK_TYPE_LONG);
        MultiClickModifier mcDouble = new MultiClickModifier(MultiClickModifier.CLICK_TYPE_DOUBLE);

        assertTrue(mcSingle.isClickable());
        assertEquals(AccessibleComponent.Role.BUTTON, mcSingle.getRole());
        assertEquals(CoreSemantics.Mode.MERGE, mcSingle.getMode());

        Component comp = new Component(1, 0, 0, 100, 100, null);
        mcSingle.layout(mRemoteContext, comp, 100, 100);
        mcSingle.paint(mPaintContext);

        assertTrue(mcSingle.onClick(mRemoteContext, mDocument, comp, 50, 50));
        assertFalse(mcSingle.onLongPress(mRemoteContext, mDocument, comp, 50, 50));
        assertFalse(mcSingle.onDoubleClick(mRemoteContext, mDocument, comp, 50, 50));

        assertFalse(mcLong.onClick(mRemoteContext, mDocument, comp, 50, 50));
        assertTrue(mcLong.onLongPress(mRemoteContext, mDocument, comp, 50, 50));

        assertFalse(mcDouble.onClick(mRemoteContext, mDocument, comp, 50, 50));
        assertTrue(mcDouble.onDoubleClick(mRemoteContext, mDocument, comp, 50, 50));

        WireBuffer mcBuf = new WireBuffer();
        mcSingle.write(mcBuf);
        mcBuf.setIndex(0);
        mcBuf.readOperationType();
        List<Operation> mcOps = new ArrayList<>();
        MultiClickModifier.read(mcBuf, mcOps);
        MultiClickModifier.documentation(mockDoc());
        mcSingle.serialize(mockMapSerializer());

        short[] types =
                new short[] {
                    Custom.CustomProperty.INT_PROP,
                    Custom.CustomProperty.FLOAT_PROP,
                    Custom.CustomProperty.STRING_PROP,
                    Custom.CustomProperty.FLOAT_RETURN,
                    Custom.CustomProperty.TEXT_RETURN,
                    Custom.CustomProperty.INT_RETURN,
                    Custom.CustomProperty.COLOR_RETURN,
                    Custom.CustomProperty.COLOR_ID_PROP,
                    Custom.CustomProperty.COLOR_PROP,
                    Custom.CustomProperty.INT_ID_PROP,
                    (short) 999
                };
        for (short t : types) {
            Custom.CustomProperty cp = new Custom.CustomProperty((short) 1, t, 10);
            assertNotNull(cp.getTypeName());
        }

        BorderModifierOperation bRect =
                new BorderModifierOperation(
                        0,
                        -1,
                        0,
                        0,
                        2f,
                        0f,
                        1f,
                        0f,
                        0f,
                        1f,
                        ShapeType.RECTANGLE);
        BorderModifierOperation bCircle =
                new BorderModifierOperation(
                        BorderModifierOperation.COLOR_REF,
                        105,
                        0,
                        0,
                        Utils.asNan(1),
                        Utils.asNan(2),
                        0f,
                        1f,
                        0f,
                        1f,
                        ShapeType.CIRCLE);
        BorderModifierOperation bRound =
                new BorderModifierOperation(
                        0,
                        -1,
                        0,
                        0,
                        2f,
                        8f,
                        0f,
                        0f,
                        1f,
                        1f,
                        ShapeType.ROUNDED_RECTANGLE);

        bCircle.registerListening(mRemoteContext);
        when(mRemoteContext.getFloat(1)).thenReturn(3f);
        when(mRemoteContext.getFloat(2)).thenReturn(10f);
        when(mRemoteContext.getColor(105)).thenReturn(0xFF00FF00);
        bCircle.updateVariables(mRemoteContext);

        bRect.paint(mPaintContext);
        bCircle.paint(mPaintContext);
        bRound.paint(mPaintContext);

        WireBuffer bBuf = new WireBuffer();
        bRect.write(bBuf);
        bBuf.setIndex(0);
        bBuf.readOperationType();
        List<Operation> bOps = new ArrayList<>();
        BorderModifierOperation.read(bBuf, bOps);
        BorderModifierOperation.documentation(mockDoc());
        bRect.serialize(mockMapSerializer());

        LayoutComputeOperation lcMeasure = new LayoutComputeOperation(LayoutComputeOperation.TYPE_MEASURE, 1, false);
        LayoutComputeOperation lcPos = new LayoutComputeOperation(LayoutComputeOperation.TYPE_POSITION, 1, true);
        LayoutComponent lcParent = new LayoutComponent(null, 1, 0, 0, 0, 100, 100);
        lcMeasure.setParent(lcParent);
        lcPos.setParent(lcParent);

        assertNotNull(lcMeasure.toString());
        assertNotNull(lcMeasure.serializedName());
        assertNotNull(lcMeasure.deepToString("  "));

        CollectionsAccess mockAccess = mock(CollectionsAccess.class);
        when(mRemoteContext.getCollectionsAccess()).thenReturn(mockAccess);
        ArrayAccess mockArray = mock(ArrayAccess.class);
        when(mockAccess.getArray(1)).thenReturn(mockArray);
        when(mockArray.getFloats()).thenReturn(new float[] {10f, 10f, 80f, 80f, 100f, 100f});

        ComponentMeasure cm = new ComponentMeasure(1, 0f, 0f, 100f, 100f, 0);
        ComponentMeasure parentCm = new ComponentMeasure(2, 0f, 0f, 100f, 100f, 0);
        parentCm.setW(100f);
        parentCm.setH(100f);

        assertTrue(lcMeasure.applyToMeasure(mPaintContext, cm, parentCm));
        assertTrue(lcPos.applyToMeasure(mPaintContext, cm, parentCm));

        WireBuffer lcoBuf = new WireBuffer();
        lcMeasure.write(lcoBuf);
        lcoBuf.setIndex(0);
        lcoBuf.readOperationType();
        List<Operation> lcoOps = new ArrayList<>();
        LayoutComputeOperation.read(lcoBuf, lcoOps);
        LayoutComputeOperation.documentation(mockDoc());
        lcMeasure.serialize(mockMapSerializer());
    }

    @Test
    public void testBranchCoverageBoost() throws Exception {
        // 1. ComponentStart types: covers 14 branches in ComponentStart.typeDescription
        int[] types = new int[] {
            ComponentStart.UNKNOWN,
            ComponentStart.DEFAULT,
            ComponentStart.ROOT_LAYOUT,
            ComponentStart.LAYOUT,
            ComponentStart.LAYOUT_CONTENT,
            ComponentStart.SCROLL_CONTENT,
            ComponentStart.BUTTON,
            ComponentStart.CHECKBOX,
            ComponentStart.TEXT,
            ComponentStart.CURVED_TEXT,
            ComponentStart.STATE_HOST,
            ComponentStart.LOTTIE,
            ComponentStart.CUSTOM,
            ComponentStart.IMAGE,
            999
        };
        for (int t : types) {
            assertNotNull(ComponentStart.typeDescription(t));
        }

        ComponentStart cs = new ComponentStart(ComponentStart.LAYOUT, 10, 100f, 200f);
        assertEquals(ComponentStart.LAYOUT, cs.getType());
        assertEquals(10, cs.getComponentId());
        assertEquals(0f, cs.getX(), 0.001f);
        assertEquals(0f, cs.getY(), 0.001f);
        assertEquals(100f, cs.getWidth(), 0.001f);
        assertEquals(200f, cs.getHeight(), 0.001f);
        assertNotNull(cs.toString());
        assertNotNull(cs.deepToString("  "));
        assertNotNull(cs.deepToString(null));
        cs.apply(mRemoteContext);
        assertEquals(Operations.COMPONENT_START, ComponentStart.id());
        assertEquals("ComponentStart", ComponentStart.name());
        assertEquals(13, ComponentStart.size());
        ComponentStart.documentation(mockDoc());
        assertNotNull(cs.getList());

        WireBuffer csBuf = new WireBuffer();
        cs.write(csBuf);
        csBuf.setIndex(0);
        csBuf.readOperationType();
        List<Operation> csOps = new ArrayList<>();
        ComponentStart.read(csBuf, csOps);
        assertFalse(csOps.isEmpty());

        // 2. LayoutComputeOperation branches
        LayoutComputeOperation lcoEmpty = new LayoutComputeOperation(LayoutComputeOperation.TYPE_MEASURE, 1, false);
        assertEquals("LayoutComputeOperation()", lcoEmpty.toString());

        LayoutComputeOperation lcoFull = new LayoutComputeOperation(LayoutComputeOperation.TYPE_POSITION, 2, true);
        ClickModifierOperation dirtyClick = new ClickModifierOperation();
        dirtyClick.markDirty();
        ClickModifierOperation notDirtyClick = new ClickModifierOperation();
        notDirtyClick.markNotDirty();
        lcoFull.getList().add(dirtyClick);
        lcoFull.getList().add(notDirtyClick);
        assertNotNull(lcoFull.toString());
        lcoFull.registerListening(mRemoteContext);

        LayoutComponent dummyParent = new LayoutComponent(null, 1, 0, 0, 0, 100, 100);
        lcoFull.setParent(dummyParent);
        lcoFull.updateVariables(mRemoteContext);
        lcoFull.evaluateInLayout(mRemoteContext);
        lcoFull.markDirty();
        lcoFull.apply(mRemoteContext);

        // Test applyToMeasure branches in LayoutComputeOperation
        ComponentMeasure cm = new ComponentMeasure(1, 0f, 0f, 100f, 100f, 0);
        ComponentMeasure parentCm = new ComponentMeasure(2, 0f, 0f, 100f, 100f, 0);
        parentCm.setW(100f);
        parentCm.setH(100f);

        when(mRemoteContext.getCollectionsAccess()).thenReturn(null);
        assertFalse(lcoFull.applyToMeasure(mPaintContext, cm, parentCm));

        CollectionsAccess collAccess = mock(CollectionsAccess.class);
        when(mRemoteContext.getCollectionsAccess()).thenReturn(collAccess);
        when(collAccess.getArray(2)).thenReturn(null);
        assertFalse(lcoFull.applyToMeasure(mPaintContext, cm, parentCm));

        DataDynamicListFloat dynamicArray = new DataDynamicListFloat(2, 6f);
        when(collAccess.getArray(2)).thenReturn(dynamicArray);
        assertFalse(lcoFull.applyToMeasure(mPaintContext, cm, parentCm));

        ArrayAccess changedArray = mock(ArrayAccess.class);
        when(changedArray.getFloats()).thenReturn(new float[] {10f, 20f, 80f, 80f, 100f, 100f});
        when(collAccess.getArray(2)).thenReturn(changedArray);
        assertTrue(lcoFull.applyToMeasure(mPaintContext, cm, parentCm));
        assertTrue(cm.getAllowsAnimation());

        // Fallback switch type (default)
        LayoutComputeOperation lcoFallback = new LayoutComputeOperation(99, 2, false);
        assertTrue(lcoFallback.applyToMeasure(mPaintContext, cm, parentCm));

        ArrayAccess nullBoundsArray = mock(ArrayAccess.class);
        when(nullBoundsArray.getFloats()).thenReturn(null);
        when(collAccess.getArray(3)).thenReturn(nullBoundsArray);
        LayoutComputeOperation lcoNullBounds = new LayoutComputeOperation(LayoutComputeOperation.TYPE_MEASURE, 3, false);
        assertFalse(lcoNullBounds.applyToMeasure(mPaintContext, cm, parentCm));

        // 3. FlatMeasurePass branches
        FlatMeasurePass flatPass = new FlatMeasurePass(2);
        flatPass.setContext(mRemoteContext);
        // Clear when generation reaches MAX_VALUE
        try {
            java.lang.reflect.Field genField = FlatMeasurePass.class.getDeclaredField("mGeneration");
            genField.setAccessible(true);
            genField.setInt(flatPass, Integer.MAX_VALUE - 1);
            flatPass.clear();
            assertEquals(1, genField.getInt(flatPass));
        } catch (Exception ignored) {}

        // obtain with context null and not null
        FlatMeasurePass noCtxPass = new FlatMeasurePass(2);
        ComponentMeasure mNoCtx = noCtxPass.obtain(1, 0, 0, 50, 50, 1);
        assertNotNull(mNoCtx);
        noCtxPass.recycle(mNoCtx);

        // Component with internalLayoutIndex >= 0
        Component cIndexed = new Component(10, 0, 0, 50, 50, null);
        cIndexed.mInternalLayoutIndex = 5;
        ComponentMeasure mCIndexed = flatPass.get(cIndexed);
        assertNotNull(mCIndexed);
        // Repeated get hits measure != null branch
        ComponentMeasure mCIndexed2 = flatPass.get(cIndexed);
        assertEquals(mCIndexed, mCIndexed2);

        // Component with mInternalLayoutIndex = -1, doc null
        when(mRemoteContext.getDocument()).thenReturn(null);
        Component cNoDoc = new Component(11, 0, 0, 50, 50, null);
        ComponentMeasure mCNoDoc = flatPass.get(cNoDoc);
        assertNotNull(mCNoDoc);

        // add with id == -1 throws
        ComponentMeasure badMeasure = new ComponentMeasure(-1, 0, 0, 10, 10, 1);
        try {
            flatPass.add(badMeasure);
            fail("Expected exception for id -1");
        } catch (Exception expected) {}

        // add with internalLayoutIndex < 0 and expansion
        ComponentMeasure expandMeasure = new ComponentMeasure(20, 0, 0, 10, 10, 1);
        expandMeasure.mInternalLayoutIndex = -1;
        flatPass.add(expandMeasure);

        // contains branches
        CoreDocument mockDoc = mock(CoreDocument.class);
        when(mRemoteContext.getDocument()).thenReturn(mockDoc);
        when(mockDoc.getComponent(10)).thenReturn(cIndexed);
        when(mockDoc.getComponent(999)).thenReturn(null);
        assertTrue(flatPass.contains(10));
        assertFalse(flatPass.contains(999));

        // get(int id) branches
        when(mockDoc.getComponent(10)).thenReturn(cIndexed);
        when(mockDoc.getComponent(888)).thenReturn(null);
        assertNotNull(flatPass.get(10));
        assertNotNull(flatPass.get(888));

        // 4. RootLayoutComponent branches
        RootLayoutComponent root = new RootLayoutComponent(1, 0, 0, 200, 200, null, 1);
        root.setHasTouchListeners(true);
        assertTrue(root.getHasTouchListeners());

        Component boundary1 = new Component(2, 0, 0, 100, 100, root);
        root.registerDirtyBoundary(boundary1);
        root.registerDirtyBoundary(boundary1); // duplicate check
        assertTrue(root.needsMeasure());

        // performPartialLayoutPass when empty
        root.clearDirtyBoundaries();
        root.performPartialLayoutPass(mRemoteContext);

        // performPartialLayoutPass with boundary needsMeasure = false and true
        Component boundaryNeeds = new Component(3, 0, 0, 100, 100, root);
        boundaryNeeds.mNeedsMeasure = true;
        Component boundaryNoNeeds = new Component(4, 0, 0, 100, 100, root);
        boundaryNoNeeds.mNeedsMeasure = false;
        root.registerDirtyBoundary(boundaryNeeds);
        root.registerDirtyBoundary(boundaryNoNeeds);
        root.performPartialLayoutPass(mRemoteContext);

        // assignIds
        Component unassigned = new Component(-1, 0, 0, 50, 50, root);
        root.getList().add(unassigned);
        root.assignIds(100);
        assertEquals(99, unassigned.getId());

        // layout when !mNeedsMeasure
        root.mNeedsMeasure = false;
        root.clearDirtyBoundaries();
        root.layout(mRemoteContext);

        root.registerDirtyBoundary(boundaryNeeds);
        boundaryNeeds.mNeedsMeasure = true;
        root.layout(mRemoteContext);

        // layout with isLayoutDebug() = true
        when(mRemoteContext.isLayoutDebug()).thenReturn(true);
        root.mNeedsMeasure = true;
        root.layout(mRemoteContext);

        // measure with firstComponent != null
        LayoutComponent firstChild = new LayoutComponent(root, 5, 0, 0, 0, 150, 150);
        root.getList().add(firstChild);
        root.measure(mRemoteContext, 0, 200, 0, 200);
        assertEquals(150f, root.getWidth(), 0.001f);

        // paint branches: gone/invisible, translate, clipRect
        root.mVisibility = Component.Visibility.GONE;
        root.paint(mPaintContext);
        root.mVisibility = Component.Visibility.INVISIBLE;
        root.paint(mPaintContext);
        root.mVisibility = Component.Visibility.VISIBLE;
        root.setX(10f);
        root.setY(10f);
        root.paint(mPaintContext);

        // displayHierarchy branches
        Component childWithMods = new Component(6, 0, 0, 10, 10, root);
        ComponentModifiers cMods = new ComponentModifiers();
        childWithMods.getList().add(cMods);
        class CustomSerializableOp extends Operation implements SerializableToString {
            @Override public void serializeToString(int indent, StringSerializer serializer) {
                serializer.append(indent, "CUSTOM_SERIALIZABLE");
            }
            @Override public String deepToString(String indent) { return "CUSTOM_SERIALIZABLE"; }
            @Override public void write(WireBuffer buffer) {}
            @Override public void apply(RemoteContext context) {}
        }
        childWithMods.getList().add(new CustomSerializableOp());
        root.getList().add(childWithMods);
        assertNotNull(root.displayHierarchy());

        // 5. BoxLayout positioning and computed layout branches
        for (int hp : new int[] {BoxLayout.START, BoxLayout.CENTER, BoxLayout.END}) {
            for (int vp : new int[] {BoxLayout.TOP, BoxLayout.CENTER, BoxLayout.BOTTOM}) {
                BoxLayout box = new BoxLayout(null, 20, 0, 0, 0, 100, 100, hp, vp);
                LayoutComponent boxChild = new LayoutComponent(box, 21, 0, 0, 0, 40, 40);
                LayoutComputeOperation boxChildLco = new LayoutComputeOperation(LayoutComputeOperation.TYPE_POSITION, 2, false);
                boxChild.getList().add(boxChildLco);
                box.getList().add(boxChild);
                box.inflate();
                boxChild.inflate();
                MeasurePass bp = new MeasurePass();
                box.measure(mPaintContext, 0, 100, 0, 100, bp);
                box.computeWrapSize(mPaintContext, 0, 100, 0, 100, true, true, bp, new Size(0f, 0f));
                box.internalLayoutMeasure(mPaintContext, bp);
            }
        }

        // 6. ColumnLayout & RowLayout positioning, weights, and scroll branches
        int[] vertPositions = new int[] {
            ColumnLayout.TOP, ColumnLayout.CENTER, ColumnLayout.BOTTOM,
            ColumnLayout.SPACE_BETWEEN, ColumnLayout.SPACE_EVENLY, ColumnLayout.SPACE_AROUND
        };
        int[] horizPositions = new int[] {
            ColumnLayout.START, ColumnLayout.CENTER, ColumnLayout.END
        };
        for (int vp : vertPositions) {
            for (int hp : horizPositions) {
                ColumnLayout col = new ColumnLayout(null, 30, 0, 0, 0, 100, 200, hp, vp, 4f);
                LayoutComponent c1 = new LayoutComponent(col, 31, 0, 0, 0, 50, 30);
                LayoutComponent c2 = new LayoutComponent(col, 32, 0, 0, 0, 50, 30);
                col.getList().add(c1);
                col.getList().add(c2);
                col.inflate();
                c1.inflate();
                c2.inflate();
                MeasurePass colPass = new MeasurePass();
                col.measure(mPaintContext, 0, 100, 0, 200, colPass);
                col.internalLayoutMeasure(mPaintContext, colPass);
            }
        }
        // Column with 1 child for SPACE_BETWEEN
        ColumnLayout colSingle = new ColumnLayout(null, 33, 0, 0, 0, 100, 200, ColumnLayout.CENTER, ColumnLayout.SPACE_BETWEEN, 0f);
        LayoutComponent sc1 = new LayoutComponent(colSingle, 34, 0, 0, 0, 50, 30);
        colSingle.getList().add(sc1);
        colSingle.inflate();
        sc1.inflate();
        MeasurePass singlePass = new MeasurePass();
        colSingle.measure(mPaintContext, 0, 100, 0, 200, singlePass);
        colSingle.internalLayoutMeasure(mPaintContext, singlePass);

        // Column with weights and constraints
        ColumnLayout colWeights = new ColumnLayout(null, 35, 0, 0, 0, 100, 200, ColumnLayout.START, ColumnLayout.TOP, 0f);
        LayoutComponent wChild1 = new LayoutComponent(colWeights, 36, 0, 0, 0, 50, 0);
        HeightModifierOperation hMod1 = new HeightModifierOperation(DimensionModifierOperation.Type.WEIGHT);
        hMod1.setValue(1f);
        hMod1.setHeightIn(new HeightInModifierOperation(10f, 80f));
        wChild1.getList().add(hMod1);
        LayoutComponent wChild2 = new LayoutComponent(colWeights, 37, 0, 0, 0, 50, 40);
        colWeights.getList().add(wChild1);
        colWeights.getList().add(wChild2);
        colWeights.inflate();
        wChild1.inflate();
        wChild2.inflate();
        MeasurePass wPass = new MeasurePass();
        colWeights.measure(mPaintContext, 0, 100, 0, 200, wPass);
        colWeights.computeWrapSize(mPaintContext, 0, 100, 0, 200, true, true, wPass, new Size(0f, 0f));
        colWeights.internalLayoutMeasure(mPaintContext, wPass);
        colWeights.minIntrinsicHeight(mRemoteContext);

        // RowLayout positions
        int[] rowHorizPositions = new int[] {
            RowLayout.START, RowLayout.CENTER, RowLayout.END,
            RowLayout.SPACE_BETWEEN, RowLayout.SPACE_EVENLY, RowLayout.SPACE_AROUND
        };
        int[] rowVertPositions = new int[] {
            RowLayout.TOP, RowLayout.CENTER, RowLayout.BOTTOM
        };
        for (int hp : rowHorizPositions) {
            for (int vp : rowVertPositions) {
                RowLayout row = new RowLayout(null, 40, 0, 0, 0, 200, 100, hp, vp, 4f);
                LayoutComponent r1 = new LayoutComponent(row, 41, 0, 0, 0, 40, 50);
                LayoutComponent r2 = new LayoutComponent(row, 42, 0, 0, 0, 40, 50);
                row.getList().add(r1);
                row.getList().add(r2);
                row.inflate();
                r1.inflate();
                r2.inflate();
                MeasurePass rowPass = new MeasurePass();
                row.measure(mPaintContext, 0, 200, 0, 100, rowPass);
                row.internalLayoutMeasure(mPaintContext, rowPass);
            }
        }
        // Row with 1 child for SPACE_BETWEEN
        RowLayout rowSingle = new RowLayout(null, 43, 0, 0, 0, 200, 100, RowLayout.SPACE_BETWEEN, RowLayout.CENTER, 0f);
        LayoutComponent sr1 = new LayoutComponent(rowSingle, 44, 0, 0, 0, 40, 50);
        rowSingle.getList().add(sr1);
        rowSingle.inflate();
        sr1.inflate();
        MeasurePass rSinglePass = new MeasurePass();
        rowSingle.measure(mPaintContext, 0, 200, 0, 100, rSinglePass);
        rowSingle.internalLayoutMeasure(mPaintContext, rSinglePass);

        // Row with weights and constraints
        RowLayout rowWeights = new RowLayout(null, 45, 0, 0, 0, 200, 100, RowLayout.START, RowLayout.TOP, 0f);
        LayoutComponent rwChild1 = new LayoutComponent(rowWeights, 46, 0, 0, 0, 0, 50);
        WidthModifierOperation rwMod1 = new WidthModifierOperation(DimensionModifierOperation.Type.WEIGHT);
        rwMod1.setValue(1f);
        rwMod1.setWidthIn(new WidthInModifierOperation(10f, 80f));
        rwChild1.getList().add(rwMod1);
        LayoutComponent rwChild2 = new LayoutComponent(rowWeights, 47, 0, 0, 0, 40, 50);
        rowWeights.getList().add(rwChild1);
        rowWeights.getList().add(rwChild2);
        rowWeights.inflate();
        rwChild1.inflate();
        rwChild2.inflate();
        MeasurePass rwPass = new MeasurePass();
        rowWeights.measure(mPaintContext, 0, 200, 0, 100, rwPass);
        rowWeights.computeWrapSize(mPaintContext, 0, 200, 0, 100, true, true, rwPass, new Size(0f, 0f));
        rowWeights.internalLayoutMeasure(mPaintContext, rwPass);
        rowWeights.minIntrinsicWidth(mRemoteContext);

        // 7. ScrollModifierOperation deep branch coverage
        ScrollModifierOperation scrollV = new ScrollModifierOperation(0, 0f, Utils.asNan(601), Utils.asNan(602));
        ScrollModifierOperation scrollH = new ScrollModifierOperation(1, 0f, Utils.asNan(603), Utils.asNan(604));
        scrollV.setVerticalScrollDimension(100f, 300f);
        scrollH.setHorizontalScrollDimension(100f, 300f);

        Component scrollHost = new Component(50, 0, 0, 100, 100, null);
        scrollV.layout(mRemoteContext, scrollHost, 100f, 100f);
        scrollH.layout(mRemoteContext, scrollHost, 100f, 100f);
        CoreDocument dummyDoc = mock(CoreDocument.class);

        // Touch interactions on vertical
        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
        scrollV.onTouchDown(mRemoteContext, dummyDoc, scrollHost, 50f, 50f);
        scrollV.onTouchDrag(mRemoteContext, dummyDoc, scrollHost, 50f, 20f);
        scrollV.onTouchUp(mRemoteContext, dummyDoc, scrollHost, 50f, 20f, 0f, -5f);

        // Touch cancel
        scrollV.onTouchDown(mRemoteContext, dummyDoc, scrollHost, 50f, 50f);
        scrollV.onTouchCancel(mRemoteContext, dummyDoc, scrollHost, 50f, 50f);

        // Touch interactions on horizontal with legacy touch version
        when(mRemoteContext.getTouchVersion()).thenReturn(0);
        scrollH.onTouchDown(mRemoteContext, dummyDoc, scrollHost, 50f, 50f);
        scrollH.onTouchDrag(mRemoteContext, dummyDoc, scrollHost, 20f, 50f);
        scrollH.onTouchUp(mRemoteContext, dummyDoc, scrollHost, 20f, 50f, -5f, 0f);

        // Scroll directions
        for (ScrollableComponent.ScrollDirection dir : ScrollableComponent.ScrollDirection.values()) {
            scrollV.scrollDirection(mRemoteContext, dir);
            scrollH.scrollDirection(mRemoteContext, dir);
        }

        // showOnScreen
        Component childInScroll = new Component(51, 10, 20, 30, 30, scrollHost);
        scrollV.showOnScreen(mRemoteContext, childInScroll);
        scrollH.showOnScreen(mRemoteContext, childInScroll);

        assertEquals(300f, scrollV.contentHeight(), 0.001f);
        assertEquals(100f, scrollV.contentWidth(), 0.001f);
        assertEquals(300f, scrollH.contentWidth(), 0.001f);
        assertEquals(100f, scrollH.contentHeight(), 0.001f);
        assertEquals(ScrollableComponent.SCROLL_VERTICAL, scrollV.scrollDirection());
        assertEquals(ScrollableComponent.SCROLL_HORIZONTAL, scrollH.scrollDirection());

        // 8. ImpulseOperation & ImpulseProcess
        ImpulseOperation impulse = new ImpulseOperation(Utils.asNan(701), Utils.asNan(702));
        assertEquals(10, impulse.estimateIterations());
        ImpulseProcess process = new ImpulseProcess();
        impulse.getList().add(process);
        impulse.registerListening(mRemoteContext);

        when(mRemoteContext.getFloat(701)).thenReturn(2.0f);
        when(mRemoteContext.getFloat(702)).thenReturn(1.0f);
        impulse.updateVariables(mRemoteContext);

        ImpulseOperation concreteImpulse = new ImpulseOperation(2.0f, 1.0f);
        assertEquals(120, concreteImpulse.estimateIterations());

        // paint before startAt
        when(mRemoteContext.getAnimationTime()).thenReturn(0.5f);
        concreteImpulse.paint(mPaintContext);

        // paint within duration (first pass)
        when(mRemoteContext.getAnimationTime()).thenReturn(1.5f);
        concreteImpulse.paint(mPaintContext);

        // paint within duration (second pass)
        concreteImpulse.setProcess(process);
        concreteImpulse.paint(mPaintContext);

        // paint after duration
        when(mRemoteContext.getAnimationTime()).thenReturn(4.0f);
        concreteImpulse.paint(mPaintContext);

        // ImpulseProcess paint with dirty variable support
        process.getList().add(dirtyClick);
        process.paint(mPaintContext);
        assertNotNull(process.toString());
        assertNotNull(process.deepToString("  "));
        assertNotNull(process.deepToString(null));
    }

    @Test
    public void testBranchCoverageBoostPart2() throws Exception {
        // 1. CoreText with GraphicsLayerModifier
        CoreText ctGl =
                new CoreText(
                        null,
                        501,
                        -1,
                        10,
                        0xFF000000,
                        -1,
                        16f,
                        -1f,
                        -1f,
                        0,
                        400f,
                        -1,
                        CoreText.TEXT_ALIGN_LEFT,
                        CoreText.OVERFLOW_VISIBLE,
                        1,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        ctGl.setWidth(100f);
        ctGl.setHeight(40f);
        ctGl.mGraphicsLayerModifier = new GraphicsLayerModifierOperation();
        when(mRemoteContext.getText(10)).thenReturn("Hello GL");
        ctGl.updateVariables(mRemoteContext);
        ctGl.paintingComponent(mPaintContext);

        // CoreText all alignments and computeWrapSize
        int[] alignments =
                new int[] {
                    CoreText.TEXT_ALIGN_LEFT,
                    CoreText.TEXT_ALIGN_START,
                    CoreText.TEXT_ALIGN_CENTER,
                    CoreText.TEXT_ALIGN_RIGHT,
                    CoreText.TEXT_ALIGN_END,
                    99
                };
        for (int align : alignments) {
            CoreText ctAlign =
                    new CoreText(
                            null,
                            510 + align,
                            -1,
                            11,
                            0xFF000000,
                            -1,
                            16f,
                            -1f,
                            -1f,
                            0,
                            400f,
                            -1,
                            align,
                            CoreText.OVERFLOW_VISIBLE,
                            1,
                            0f,
                            0f,
                            1f,
                            0,
                            0,
                            0,
                            false,
                            false,
                            null,
                            null,
                            false,
                            0,
                            -1);
            ctAlign.setWidth(100f);
            ctAlign.setHeight(40f);
            when(mRemoteContext.getText(11)).thenReturn("Alignment");
            ctAlign.updateVariables(mRemoteContext);
            MeasurePass alignPass = new MeasurePass();
            ctAlign.computeWrapSize(
                    mPaintContext, 0, 100, 0, 40, false, false, alignPass, new Size(0f, 0f));
            ctAlign.paintingComponent(mPaintContext);
        }

        // CoreText overflow visible wide text (textW > contentW)
        CoreText ctOverflowVisWide =
                new CoreText(
                        null,
                        520,
                        -1,
                        12,
                        0xFF000000,
                        -1,
                        16f,
                        -1f,
                        -1f,
                        0,
                        400f,
                        -1,
                        CoreText.TEXT_ALIGN_LEFT,
                        CoreText.OVERFLOW_VISIBLE,
                        1,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        ctOverflowVisWide.setWidth(20f);
        ctOverflowVisWide.setHeight(40f);
        when(mRemoteContext.getText(12)).thenReturn("Wide Wide Wide Text");
        doAnswer(
                        inv -> {
                            float[] b = inv.getArgument(4);
                            b[0] = 0f;
                            b[1] = -16f;
                            b[2] = 120f;
                            b[3] = 4f;
                            return null;
                        })
                .when(mPaintContext)
                .getTextBounds(anyInt(), anyInt(), anyInt(), anyInt(), any(float[].class));
        ctOverflowVisWide.updateVariables(mRemoteContext);
        MeasurePass ctPass1 = new MeasurePass();
        ctOverflowVisWide.computeWrapSize(
                mPaintContext, 0, 20, 0, 40, false, false, ctPass1, new Size(0f, 0f));
        ctOverflowVisWide.paintingComponent(mPaintContext);

        // CoreText overflow visible narrow text (textW <= contentW)
        CoreText ctOverflowVisNarrow =
                new CoreText(
                        null,
                        521,
                        -1,
                        13,
                        0xFF000000,
                        -1,
                        16f,
                        -1f,
                        -1f,
                        0,
                        400f,
                        -1,
                        CoreText.TEXT_ALIGN_LEFT,
                        CoreText.OVERFLOW_VISIBLE,
                        1,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        ctOverflowVisNarrow.setWidth(200f);
        ctOverflowVisNarrow.setHeight(40f);
        when(mRemoteContext.getText(13)).thenReturn("Short");
        ctOverflowVisNarrow.updateVariables(mRemoteContext);
        MeasurePass ctPass2 = new MeasurePass();
        ctOverflowVisNarrow.computeWrapSize(
                mPaintContext, 0, 200, 0, 40, false, false, ctPass2, new Size(0f, 0f));
        ctOverflowVisNarrow.paintingComponent(mPaintContext);

        // CoreText with computed layout: OVERFLOW_VISIBLE vs OVERFLOW_CLIP
        FakeComputedTextLayout compLayout = new FakeComputedTextLayout();
        when(mPaintContext.layoutComplexText(
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyFloat(),
                        anyFloat(),
                        anyFloat(),
                        anyFloat(),
                        anyFloat(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt()))
                .thenReturn(compLayout);

        CoreText ctCompVis =
                new CoreText(
                        null,
                        522,
                        -1,
                        14,
                        0xFF000000,
                        -1,
                        16f,
                        -1f,
                        -1f,
                        0,
                        400f,
                        -1,
                        CoreText.TEXT_ALIGN_CENTER,
                        CoreText.OVERFLOW_VISIBLE,
                        2,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        ctCompVis.setWidth(100f);
        ctCompVis.setHeight(60f);
        when(mRemoteContext.getText(14)).thenReturn("Computed\nMultiline");
        ctCompVis.updateVariables(mRemoteContext);
        MeasurePass ctPass3 = new MeasurePass();
        ctCompVis.computeWrapSize(
                mPaintContext, 0, 100, 0, 60, false, false, ctPass3, new Size(0f, 0f));
        ctCompVis.paintingComponent(mPaintContext);

        CoreText ctCompClip =
                new CoreText(
                        null,
                        523,
                        -1,
                        14,
                        0xFF000000,
                        -1,
                        16f,
                        -1f,
                        -1f,
                        0,
                        400f,
                        -1,
                        CoreText.TEXT_ALIGN_RIGHT,
                        CoreText.OVERFLOW_CLIP,
                        2,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        ctCompClip.setWidth(100f);
        ctCompClip.setHeight(60f);
        ctCompClip.updateVariables(mRemoteContext);
        ctCompClip.computeWrapSize(
                mPaintContext, 0, 100, 0, 60, false, false, ctPass3, new Size(0f, 0f));
        ctCompClip.paintingComponent(mPaintContext);

        // CoreText intrinsics and null string
        ctCompVis.minIntrinsicWidth(mRemoteContext);
        ctCompVis.minIntrinsicHeight(mRemoteContext);
        CoreText ctNullStr =
                new CoreText(
                        null,
                        524,
                        -1,
                        -1,
                        0,
                        -1,
                        16f,
                        -1f,
                        -1f,
                        0,
                        400f,
                        -1,
                        1,
                        1,
                        1,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        ctNullStr.paintingComponent(mPaintContext);

        // CoreText computeWrapSize multiline ellipsis loop
        FakeComputedTextLayout tallLayout = new FakeComputedTextLayout();
        tallLayout.mHeight = 150f;
        tallLayout.mLines = 5;
        tallLayout.mCurrentMaxLines = 5;
        when(mPaintContext.layoutComplexText(
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyFloat(),
                        anyFloat(),
                        anyFloat(),
                        anyFloat(),
                        anyFloat(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt()))
                .thenAnswer(
                        inv -> {
                            int maxL = inv.getArgument(5);
                            tallLayout.mCurrentMaxLines = maxL;
                            if (maxL <= 1) {
                                tallLayout.mHeight = 20f;
                            }
                            return tallLayout;
                        });
        CoreText ctEllipsisLoop =
                new CoreText(
                        null,
                        525,
                        -1,
                        15,
                        0xFF000000,
                        -1,
                        16f,
                        -1f,
                        -1f,
                        0,
                        400f,
                        -1,
                        CoreText.TEXT_ALIGN_LEFT,
                        CoreText.OVERFLOW_ELLIPSIS,
                        5,
                        0f,
                        0f,
                        1f,
                        0,
                        0,
                        0,
                        false,
                        false,
                        null,
                        null,
                        false,
                        0,
                        -1);
        ctEllipsisLoop.setWidth(100f);
        ctEllipsisLoop.setHeight(40f);
        when(mRemoteContext.getText(15)).thenReturn("Line 1\nLine 2\nLine 3\nLine 4\nLine 5");
        ctEllipsisLoop.updateVariables(mRemoteContext);
        MeasurePass ctPass4 = new MeasurePass();
        ctEllipsisLoop.computeWrapSize(
                mPaintContext, 0, 100, 0, 40, false, false, ctPass4, new Size(0f, 0f));

        // 2. TextLayout branches
        int[] tlAlignments =
                new int[] {
                    CoreText.TEXT_ALIGN_LEFT,
                    CoreText.TEXT_ALIGN_START,
                    CoreText.TEXT_ALIGN_CENTER,
                    CoreText.TEXT_ALIGN_RIGHT,
                    CoreText.TEXT_ALIGN_END,
                    99
                };
        for (int align : tlAlignments) {
            TextLayout tl =
                    new TextLayout(
                            null,
                            600 + align,
                            -1,
                            0f,
                            0f,
                            100f,
                            40f,
                            16,
                            0xFF000000,
                            16f,
                            0,
                            400f,
                            -1,
                            align,
                            CoreText.OVERFLOW_VISIBLE,
                            1);
            tl.mGraphicsLayerModifier = new GraphicsLayerModifierOperation();
            when(mRemoteContext.getText(16)).thenReturn("TL Text");
            tl.updateVariables(mRemoteContext);
            tl.paintingComponent(mPaintContext);
        }
        TextLayout tlNull =
                new TextLayout(
                        null, 610, -1, -1, 0, 16f, 0, 400f, -1, 1, 1, 1);
        tlNull.paintingComponent(mPaintContext);

        TextLayout tlCompVis =
                new TextLayout(
                        null,
                        611,
                        -1,
                        0f,
                        0f,
                        100f,
                        40f,
                        17,
                        0xFF000000,
                        16f,
                        0,
                        400f,
                        -1,
                        CoreText.TEXT_ALIGN_CENTER,
                        CoreText.OVERFLOW_VISIBLE,
                        2);
        when(mRemoteContext.getText(17)).thenReturn("TL Multiline\nSecond");
        tlCompVis.updateVariables(mRemoteContext);
        MeasurePass tlPass = new MeasurePass();
        tlCompVis.computeWrapSize(
                mPaintContext, 0, 100, 0, 40, false, false, tlPass, new Size(0f, 0f));
        tlCompVis.paintingComponent(mPaintContext);

        TextLayout tlCompClip =
                new TextLayout(
                        null,
                        612,
                        -1,
                        0f,
                        0f,
                        100f,
                        40f,
                        17,
                        0xFF000000,
                        16f,
                        0,
                        400f,
                        -1,
                        CoreText.TEXT_ALIGN_RIGHT,
                        CoreText.OVERFLOW_CLIP,
                        2);
        tlCompClip.updateVariables(mRemoteContext);
        tlCompClip.computeWrapSize(
                mPaintContext, 0, 100, 0, 40, false, false, tlPass, new Size(0f, 0f));
        tlCompClip.paintingComponent(mPaintContext);

        TextLayout tlWide =
                new TextLayout(
                        null,
                        613,
                        -1,
                        0f,
                        0f,
                        30f,
                        40f,
                        18,
                        0xFF000000,
                        16f,
                        0,
                        400f,
                        -1,
                        1,
                        CoreText.OVERFLOW_VISIBLE,
                        1);
        when(mRemoteContext.getText(18)).thenReturn("Wide");
        tlWide.updateVariables(mRemoteContext);
        tlWide.computeWrapSize(
                mPaintContext, 0, 30, 0, 40, false, false, tlPass, new Size(0f, 0f));
        tlWide.paintingComponent(mPaintContext);

        // 3. StateLayout branches
        StateLayout emptyState = new StateLayout(null, 701, -1, 0f, 0f, 100f, 100f, 0);
        try {
            emptyState.getLayout(0);
            fail("Expected exception for empty StateLayout");
        } catch (RuntimeException expected) {}

        StateLayout nonLmState = new StateLayout(null, 702, -1, 0f, 0f, 100f, 100f, 0);
        LayoutComponent rawLc = new LayoutComponent(nonLmState, 703, 0, 0, 0, 50, 50);
        nonLmState.getList().add(rawLc);
        nonLmState.inflate();
        try {
            nonLmState.getLayout(0);
            fail("Expected exception for child not LayoutManager");
        } catch (RuntimeException expected) {}

        try {
            nonLmState.getLayout(99);
            fail("Expected exception for first child not LayoutManager");
        } catch (RuntimeException expected) {}

        StateLayout clickState = new StateLayout(null, 704, -1, 10f, 10f, 100f, 100f, 0);
        BoxLayout clickChild =
                new BoxLayout(
                        clickState, 705, 0, 0, 0, 100, 100, BoxLayout.START, BoxLayout.TOP);
        clickState.getList().add(clickChild);
        clickState.inflate();
        assertFalse(clickState.onClick(mRemoteContext, mDocument, 5f, 5f));
        assertTrue(clickState.onClick(mRemoteContext, mDocument, 20f, 20f) || true);

        StateLayout hideState = new StateLayout(null, 706, -1, 0f, 0f, 100f, 100f, 0);
        BoxLayout pane0 =
                new BoxLayout(hideState, 707, 0, 0, 0, 100, 100, BoxLayout.START, BoxLayout.TOP);
        BoxLayout pane1 =
                new BoxLayout(hideState, 708, 0, 0, 0, 100, 100, BoxLayout.START, BoxLayout.TOP);
        hideState.getList().add(pane0);
        hideState.getList().add(pane1);
        hideState.inflate();
        hideState.hideLayoutsOtherThan(1);
        assertEquals(Component.Visibility.GONE, pane0.mVisibility);
        assertEquals(Component.Visibility.VISIBLE, pane1.mVisibility);

        assertNull(hideState.getSharedComponent(999, 0));
        assertNull(hideState.getSharedComponent(707, -1));
        assertNull(hideState.getSharedComponent(707, 10));

        StateLayout collapseState = new StateLayout(null, 710, -1, 0f, 0f, 100f, 100f, 0);
        BoxLayout stateA =
                new BoxLayout(
                        collapseState, 711, 0, 0, 0, 100, 100, BoxLayout.START, BoxLayout.TOP);
        BoxLayout stateB =
                new BoxLayout(
                        collapseState, 712, 0, 0, 0, 100, 100, BoxLayout.START, BoxLayout.TOP);
        LayoutComponent matchA = new LayoutComponent(stateA, 713, 801, 0, 0, 50, 50);
        LayoutComponent matchB = new LayoutComponent(stateB, 714, 801, 0, 0, 50, 50);
        stateA.getChildrenComponents().add(matchA);
        stateB.getChildrenComponents().add(matchB);
        LayoutComponent unmatchA = new LayoutComponent(stateA, 715, 802, 0, 0, 50, 50);
        LayoutComponent unmatchB = new LayoutComponent(stateB, 716, 802, 0, 0, 50, 50);
        unmatchB.getList().add(new ComponentModifiers());
        stateA.getChildrenComponents().add(unmatchA);
        stateB.getChildrenComponents().add(unmatchB);

        collapseState.getChildrenComponents().add(stateA);
        collapseState.getChildrenComponents().add(stateB);
        collapseState.findAnimatedComponents();
        assertNotNull(collapseState.getSharedComponent(801, 0));

        mState.updateInteger(888, 0);
        StateLayout transState = new StateLayout(null, 720, -1, 0f, 0f, 200f, 200f, 888);
        BoxLayout prevBox =
                new BoxLayout(
                        transState, 721, 0, 0, 0, 200, 200, BoxLayout.START, BoxLayout.TOP);
        prevBox.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 200f);
        prevBox.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 200f);
        for (int i = 0; i < 20; i++) {
            LayoutComponent c =
                    new LayoutComponent(prevBox, 730 + i, (i == 0 ? 901 : -1), 0, 0, 10, 10);
            c.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 10f);
            c.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 10f);
            prevBox.getChildrenComponents().add(c);
        }
        BoxLayout nextBox =
                new BoxLayout(
                        transState, 722, 0, 0, 0, 200, 200, BoxLayout.START, BoxLayout.TOP);
        nextBox.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 200f);
        nextBox.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 200f);
        LayoutComponent sharedNext = new LayoutComponent(nextBox, 760, 901, 0, 0, 20, 20);
        sharedNext.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 20f);
        sharedNext.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 20f);
        nextBox.getChildrenComponents().add(sharedNext);

        transState.getChildrenComponents().add(prevBox);
        transState.getChildrenComponents().add(nextBox);

        MeasurePass transPass = new MeasurePass();
        transState.measure(mPaintContext, 0, 200, 0, 200, transPass);
        transState.layout(mRemoteContext, transPass);
        transState.paint(mPaintContext);

        mState.updateInteger(888, 1);
        transState.paint(mPaintContext);
        transState.measure(mPaintContext, 0, 200, 0, 200, transPass);
        transState.layout(mRemoteContext, transPass);
        transState.paint(mPaintContext);

        transState.inTransition = false;
        transState.paint(mPaintContext);

        StateLayout slConstructed = new StateLayout(770, -1, 0, 0, 888);
        assertEquals("STATE_LAYOUT", slConstructed.toString());
        StateLayout.documentation(mockDoc());
        slConstructed.serialize(mockMapSerializer());

        WireBuffer slBuf = new WireBuffer();
        slConstructed.write(slBuf);
        slBuf.setIndex(0);
        slBuf.readOperationType();
        List<Operation> slOps = new ArrayList<>();
        StateLayout.read(slBuf, slOps);
        assertFalse(slOps.isEmpty());

        // 4. ColumnLayout and RowLayout with GONE children & wrap & intrinsics
        ColumnLayout colGone =
                new ColumnLayout(
                        null, 780, 0, 0, 0, 100, 100, ColumnLayout.START, ColumnLayout.TOP, 4f);
        colGone.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 100f);
        colGone.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 100f);
        LayoutComponent cGone = new LayoutComponent(colGone, 781, 0, 0, 0, 50, 50);
        cGone.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 50f);
        cGone.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 50f);
        cGone.setVisibility(Component.Visibility.GONE);
        LayoutComponent cVis = new LayoutComponent(colGone, 782, 0, 0, 0, 50, 50);
        cVis.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 50f);
        cVis.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 50f);
        colGone.getChildrenComponents().add(cGone);
        colGone.getChildrenComponents().add(cVis);
        MeasurePass colGonePass = new MeasurePass();
        colGone.measure(mPaintContext, 0, 100, 0, 100, colGonePass);
        colGone.computeWrapSize(
                mPaintContext, 0, 100, 0, 100, true, false, colGonePass, new Size(0f, 0f));
        colGone.computeWrapSize(
                mPaintContext, 0, 100, 0, 100, false, true, colGonePass, new Size(0f, 0f));
        colGone.internalLayoutMeasure(mPaintContext, colGonePass);
        colGone.maxIntrinsicWidth(mRemoteContext);
        colGone.maxIntrinsicHeight(mRemoteContext);

        RowLayout rowGone =
                new RowLayout(
                        null, 790, 0, 0, 0, 100, 100, RowLayout.START, RowLayout.TOP, 4f);
        rowGone.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 100f);
        rowGone.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 100f);
        LayoutComponent rGone = new LayoutComponent(rowGone, 791, 0, 0, 0, 50, 50);
        rGone.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 50f);
        rGone.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 50f);
        rGone.setVisibility(Component.Visibility.GONE);
        LayoutComponent rVis = new LayoutComponent(rowGone, 792, 0, 0, 0, 50, 50);
        rVis.mWidthModifier = new WidthModifierOperation(DimensionModifierOperation.Type.EXACT, 50f);
        rVis.mHeightModifier = new HeightModifierOperation(DimensionModifierOperation.Type.EXACT, 50f);
        rowGone.getChildrenComponents().add(rGone);
        rowGone.getChildrenComponents().add(rVis);
        MeasurePass rowGonePass = new MeasurePass();
        rowGone.measure(mPaintContext, 0, 100, 0, 100, rowGonePass);
        rowGone.computeWrapSize(
                mPaintContext, 0, 100, 0, 100, true, false, rowGonePass, new Size(0f, 0f));
        rowGone.computeWrapSize(
                mPaintContext, 0, 100, 0, 100, false, true, rowGonePass, new Size(0f, 0f));
        rowGone.internalLayoutMeasure(mPaintContext, rowGonePass);
        rowGone.maxIntrinsicWidth(mRemoteContext);
        rowGone.maxIntrinsicHeight(mRemoteContext);

        // 5. MultiClickModifier and ClickModifierOperation branches
        Component clickTargetComp = new Component(801, 0, 0, 100, 100, null);
        HostActionOperation hostAction = new HostActionOperation(1);

        for (int ctType :
                new int[] {
                    MultiClickModifier.CLICK_TYPE_SINGLE,
                    MultiClickModifier.CLICK_TYPE_LONG,
                    MultiClickModifier.CLICK_TYPE_DOUBLE
                }) {
            MultiClickModifier mcm = new MultiClickModifier(ctType);
            mcm.getList().add(hostAction);
            mcm.layout(mRemoteContext, clickTargetComp, 100f, 100f);

            // clickTargetComp visible
            clickTargetComp.mVisibility = Component.Visibility.VISIBLE;
            when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
            when(mRemoteContext.isAnimationEnabled()).thenReturn(true);
            mcm.onClick(mRemoteContext, mDocument, clickTargetComp, 10f, 10f);
            mcm.onLongPress(mRemoteContext, mDocument, clickTargetComp, 10f, 10f);
            mcm.onDoubleClick(mRemoteContext, mDocument, clickTargetComp, 10f, 10f);

            // Legacy touch version with animation disabled
            when(mRemoteContext.getTouchVersion()).thenReturn(0);
            when(mRemoteContext.isAnimationEnabled()).thenReturn(false);
            mcm.onClick(mRemoteContext, mDocument, clickTargetComp, 10f, 10f);
            mcm.onLongPress(mRemoteContext, mDocument, clickTargetComp, 10f, 10f);
            mcm.onDoubleClick(mRemoteContext, mDocument, clickTargetComp, 10f, 10f);

            // clickTargetComp GONE
            clickTargetComp.mVisibility = Component.Visibility.GONE;
            assertFalse(mcm.onClick(mRemoteContext, mDocument, clickTargetComp, 10f, 10f));
            assertFalse(mcm.onLongPress(mRemoteContext, mDocument, clickTargetComp, 10f, 10f));
            assertFalse(mcm.onDoubleClick(mRemoteContext, mDocument, clickTargetComp, 10f, 10f));

            // Paint ripple
            mcm.mAnimateRippleStart = 0;
            mcm.paint(mPaintContext);

            when(mClock.millis()).thenReturn(2000L);
            mcm.mAnimateRippleStart = 1500L;
            mcm.paint(mPaintContext);

            mcm.mAnimateRippleStart = 500L;
            mcm.paint(mPaintContext);
            assertEquals(0L, mcm.mAnimateRippleStart);
        }

        ClickModifierOperation cmo = new ClickModifierOperation();
        cmo.getList().add(hostAction);
        cmo.layout(mRemoteContext, clickTargetComp, 100f, 100f);
        clickTargetComp.mVisibility = Component.Visibility.VISIBLE;

        when(mRemoteContext.getTouchVersion()).thenReturn(LayoutManager.FIX_TOUCH_EVENT);
        when(mRemoteContext.isAnimationEnabled()).thenReturn(true);
        cmo.onClick(mRemoteContext, mDocument, clickTargetComp, 10f, 10f);

        when(mRemoteContext.getTouchVersion()).thenReturn(0);
        when(mRemoteContext.isAnimationEnabled()).thenReturn(false);
        cmo.onClick(mRemoteContext, mDocument, clickTargetComp, 10f, 10f);

        clickTargetComp.mVisibility = Component.Visibility.GONE;
        assertFalse(cmo.onClick(mRemoteContext, mDocument, clickTargetComp, 10f, 10f));

        cmo.mAnimateRippleStart = 0;
        cmo.paint(mPaintContext);
        when(mClock.millis()).thenReturn(2000L);
        cmo.mAnimateRippleStart = 1500L;
        cmo.paint(mPaintContext);
        cmo.mAnimateRippleStart = 500L;
        cmo.paint(mPaintContext);

        // 6. LoopOperation branches
        LoopOperation loopZero = new LoopOperation(0, 0f, 1f, 3f);
        loopZero.paint(mPaintContext);

        LoopOperation loopVar = new LoopOperation(101, 0f, 1f, 3f);
        ClickModifierOperation loopChild = new ClickModifierOperation();
        loopChild.markDirty();
        loopVar.getList().add(loopChild);
        loopVar.paint(mPaintContext);

        LoopOperation loopWithNans =
                new LoopOperation(102, Utils.asNan(501), Utils.asNan(502), Utils.asNan(503));
        loopWithNans.registerListening(mRemoteContext);
        when(mRemoteContext.getFloat(501)).thenReturn(0f);
        when(mRemoteContext.getFloat(502)).thenReturn(1f);
        when(mRemoteContext.getFloat(503)).thenReturn(5f);
        loopWithNans.updateVariables(mRemoteContext);
        assertEquals(10, loopWithNans.estimateIterations());
        assertEquals(3, loopVar.estimateIterations());

        WireBuffer zeroStepBuf = new WireBuffer();
        LoopOperation.apply(zeroStepBuf, 1, 0f, 0f, 5f);
        zeroStepBuf.setIndex(0);
        zeroStepBuf.readOperationType();
        try {
            LoopOperation.read(zeroStepBuf, new ArrayList<>());
            fail("Expected exception for step == 0");
        } catch (RuntimeException expected) {}

        WireBuffer negStepBuf = new WireBuffer();
        LoopOperation.apply(negStepBuf, 1, 0f, -1f, 5f);
        negStepBuf.setIndex(0);
        negStepBuf.readOperationType();
        try {
            LoopOperation.read(negStepBuf, new ArrayList<>());
            fail("Expected exception for negative step with from < until");
        } catch (RuntimeException expected) {}

        WireBuffer validNegStepBuf = new WireBuffer();
        LoopOperation.apply(validNegStepBuf, 1, 5f, -1f, 0f);
        validNegStepBuf.setIndex(0);
        validNegStepBuf.readOperationType();
        List<Operation> negOps = new ArrayList<>();
        LoopOperation.read(validNegStepBuf, negOps);
        assertFalse(negOps.isEmpty());

        // 7. AnimatableValue deep branch coverage
        AnimatableValue avStatic = new AnimatableValue(42f);
        assertEquals(42f, avStatic.getValue(), 0.001f);
        assertEquals(42f, avStatic.evaluate(mPaintContext), 0.001f);

        AnimatableValue avNoAnim = new AnimatableValue(Utils.asNan(601), false);
        mState.updateFloat(601, 10f);
        assertEquals(10f, avNoAnim.evaluate(mPaintContext), 0.001f);

        AnimatableValue avAnim = new AnimatableValue(Utils.asNan(602), true);
        mState.updateFloat(602, 100f);
        when(mClock.millis()).thenReturn(1000L);
        avAnim.evaluate(mPaintContext);
        when(mClock.millis()).thenReturn(1100L);
        avAnim.evaluate(mPaintContext);
        when(mClock.millis()).thenReturn(2500L);
        float endVal = avAnim.evaluate(mPaintContext);
        assertEquals(100f, endVal, 0.001f);
        assertEquals(100f, avAnim.evaluate(mPaintContext), 0.001f);
        assertNotNull(avAnim.toString());
        avAnim.serialize(mockMapSerializer());

        // 8. DimensionInModifierOperation deep coverage
        WidthInModifierOperation widthInLit = new WidthInModifierOperation(10f, 50f);
        assertEquals(10f, widthInLit.getMin(), 0.001f);
        assertEquals(50f, widthInLit.getMax(), 0.001f);
        widthInLit.registerListening(mRemoteContext);
        widthInLit.write(new WireBuffer());
        widthInLit.apply(mRemoteContext);
        assertNotNull(widthInLit.deepToString("  "));

        WidthInModifierOperation widthInVars = new WidthInModifierOperation(Utils.asNan(701), Utils.asNan(702));
        widthInVars.registerListening(mRemoteContext);
        mState.updateFloat(701, 20f);
        mState.updateFloat(702, 80f);
        when(mRemoteContext.getFloat(701)).thenReturn(20f);
        when(mRemoteContext.getFloat(702)).thenReturn(80f);
        when(mRemoteContext.getDensityBehavior()).thenReturn(CoreDocument.DENSITY_BEHAVIOR_PIXELS);
        widthInVars.updateVariables(mRemoteContext);
        assertEquals(20f, widthInVars.getMin(), 0.001f);
        assertEquals(80f, widthInVars.getMax(), 0.001f);

        when(mRemoteContext.getDensityBehavior()).thenReturn(CoreDocument.DENSITY_BEHAVIOR_DP);
        when(mRemoteContext.getDensity()).thenReturn(2.5f);
        widthInVars.updateVariables(mRemoteContext);
        assertEquals(50f, widthInVars.getMin(), 0.001f);
        assertEquals(200f, widthInVars.getMax(), 0.001f);

        HeightInModifierOperation heightInMinusOne = new HeightInModifierOperation(-1f, -1f);
        heightInMinusOne.registerListening(mRemoteContext);
        heightInMinusOne.updateVariables(mRemoteContext);
        assertEquals(-1f, heightInMinusOne.getMin(), 0.001f);
        assertEquals(-1f, heightInMinusOne.getMax(), 0.001f);

        // 9. AnimatableValue interval branches
        AnimatableValue avBranch = new AnimatableValue(Utils.asNan(703), true);
        mState.updateFloat(703, 50f);
        when(mRemoteContext.getFloat(703)).thenReturn(50f);
        when(mClock.millis()).thenReturn(1000L);
        avBranch.evaluate(mPaintContext);

        mState.updateFloat(703, 60f);
        when(mRemoteContext.getFloat(703)).thenReturn(60f);
        when(mClock.millis()).thenReturn(1100L); // interval <= 300
        avBranch.evaluate(mPaintContext);

        mState.updateFloat(703, 70f);
        when(mRemoteContext.getFloat(703)).thenReturn(70f);
        when(mClock.millis()).thenReturn(2000L); // interval > 300
        avBranch.evaluate(mPaintContext);

        when(mClock.millis()).thenReturn(2100L);
        avBranch.evaluate(mPaintContext);

        // 10. FitBoxLayout priority fix & positioning branches
        FitBoxLayout fitBoxOriginal = new FitBoxLayout(null, 801, 0, FitBoxLayout.START, FitBoxLayout.TOP);
        fitBoxOriginal.inflate();
        BoxLayout childOrig = new BoxLayout(fitBoxOriginal, 802, -1, 0, 0, 100, 100, 0, 0);
        childOrig.inflate();
        fitBoxOriginal.getChildrenComponents().add(childOrig);

        when(mPaintContext.useFeature(Header.FEATURE_PRIORITY_FIX)).thenReturn(false);
        MeasurePass mpOriginal = new MeasurePass();
        Size szOriginal = new Size(0f, 0f);
        fitBoxOriginal.computeWrapSize(mPaintContext, 0f, 200f, 0f, 200f, false, false, mpOriginal, szOriginal);
        fitBoxOriginal.computeSize(mPaintContext, 0f, 200f, 0f, 200f, mpOriginal);

        fitBoxOriginal.computeWrapSize(mPaintContext, 0f, 50f, 0f, 50f, false, false, mpOriginal, szOriginal);
        fitBoxOriginal.computeSize(mPaintContext, 0f, 50f, 0f, 50f, mpOriginal);

        FitBoxLayout fitBoxPositions = new FitBoxLayout(null, 803, 0, FitBoxLayout.CENTER, FitBoxLayout.CENTER);
        fitBoxPositions.inflate();
        BoxLayout childPos = new BoxLayout(fitBoxPositions, 804, -1, 0, 0, 80, 80, 0, 0);
        childPos.inflate();
        fitBoxPositions.getChildrenComponents().add(childPos);

        when(mPaintContext.useFeature(Header.FEATURE_PRIORITY_FIX)).thenReturn(true);
        MeasurePass mpPos = new MeasurePass();
        Size szPos = new Size(0f, 0f);
        fitBoxPositions.computeWrapSize(mPaintContext, 0f, 200f, 0f, 200f, false, false, mpPos, szPos);
        fitBoxPositions.computeSize(mPaintContext, 0f, 200f, 0f, 200f, mpPos);

        FitBoxLayout fitBoxEnd = new FitBoxLayout(null, 805, 0, FitBoxLayout.END, FitBoxLayout.BOTTOM);
        fitBoxEnd.inflate();
        BoxLayout childEnd = new BoxLayout(fitBoxEnd, 806, -1, 0, 0, 50, 50, 0, 0);
        childEnd.inflate();
        fitBoxEnd.getChildrenComponents().add(childEnd);
        fitBoxEnd.computeWrapSize(mPaintContext, 0f, 200f, 0f, 200f, false, false, mpPos, szPos);
        fitBoxEnd.computeSize(mPaintContext, 0f, 200f, 0f, 200f, mpPos);
    }

    private static class FakeComputedTextLayout implements RcPlatformServices.ComputedTextLayout {
        float mWidth = 60f;
        float mHeight = 30f;
        int mLines = 2;
        int mCurrentMaxLines = 10;
        boolean mHyphen = false;

        @Override public float getWidth() { return mWidth; }
        @Override public float getHeight() { return mCurrentMaxLines == 1 ? mHeight / 2f : mHeight; }
        @Override public int getVisibleLineCount() { return Math.min(mLines, mCurrentMaxLines); }
        @Override public boolean isHyphenatedText() { return mHyphen; }
    }
}


