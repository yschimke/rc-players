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

package androidx.compose.remote.creation.json;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.compose.remote.core.CoreDocument;
import androidx.compose.remote.core.Operation;
import androidx.compose.remote.core.PaintContext;
import androidx.compose.remote.core.RcPlatformServices;
import androidx.compose.remote.core.RemoteClock;
import androidx.compose.remote.core.RemoteComposeBuffer;
import androidx.compose.remote.core.RemoteContext;
import androidx.compose.remote.core.operations.RootContentBehavior;
import androidx.compose.remote.core.operations.Theme;
import androidx.compose.remote.core.operations.layout.Component;
import androidx.compose.remote.core.operations.layout.Container;
import androidx.compose.remote.core.operations.paint.PaintBundle;
import androidx.compose.remote.core.operations.paint.PaintChangeAdapter;
import androidx.compose.remote.core.operations.paint.PaintChanges;
import androidx.compose.remote.creation.RemoteComposeWriter;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

public class ConformanceGoldGeneratorTest {

    /**
     * Origin of the harness timeline, in milliseconds. Every phase of gold generation --
     * the base frame loop, resize steps, animation frames and interactions -- measures
     * from this instant, so an operation that latches a start time in one phase stays
     * consistent with the frames captured in the next.
     */
    private static final long BASE_TIME_MILLIS = 10000L;

    /**
     * Origin of the warm-up frames, one second ahead of {@link #BASE_TIME_MILLIS}. The warm-up
     * frames only exist to settle the layout, so they are kept off the sampled part of the
     * timeline while still preceding it.
     */
    private static final long WARM_UP_START_MILLIS = BASE_TIME_MILLIS - 1000L;

    static class ComponentInfo {
        int id;
        String kind;
        int depth;
        float x;
        float y;
        float w;
        float h;
        float scrollX;
        float scrollY;
        boolean isGone;
        String visibility;

        ComponentInfo(int id, String kind, int depth, float x, float y, float w, float h, float scrollX, float scrollY) {
            this.id = id;
            this.kind = kind;
            this.depth = depth;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.isGone = false;
            this.visibility = "VISIBLE";
        }
    }

    /**
     * A clock whose {@code millis()} follows the harness timeline ({@link
     * RemoteContext#currentTime}) instead of the wall clock.
     *
     * <p>Operations that animate against real time -- {@code MarqueeModifierOperation}, the ripple
     * in {@code ClickModifierOperation}, {@code AnimatableValue} -- call {@code
     * context.getClock().millis()}. Left on {@link RemoteClock#SYSTEM} those operations sample the
     * machine's wall clock, which makes anything they influence unreproducible from one generator
     * run to the next. Binding the clock to {@code currentTime} means the same timeline the
     * harness already advances for {@code setAnimationTime()} also drives them, so a gold file can
     * assert on time-varying values.
     */
    private static class HarnessClock implements RemoteClock {
        @Nullable RemoteContext mContext;

        @Override
        public long millis() {
            return mContext == null ? 0L : mContext.currentTime;
        }

        @Override
        public long nanoTime() {
            return millis() * 1_000_000L;
        }

        @Override
        public @NonNull String getZoneId() {
            return "UTC";
        }

        @Override
        public @NonNull TimeSnapshot snapshot(@Nullable Long millis) {
            return RemoteClock.SYSTEM.snapshot(
                    millis == null ? millis() : millis);
        }
    }

    private static class HeadlessPaintContext extends PaintContext {
        HeadlessPaintContext(RemoteContext ctx) {
            super(ctx);
        }
        @Override public void drawBitmap(int imageId, int srcLeft, int srcTop, int srcRight, int srcBottom, int dstLeft, int dstTop, int dstRight, int dstBottom, int cdId) {}
        @Override public void scale(float scaleX, float scaleY) {}
        @Override public void translate(float translateX, float translateY) {}
        @Override public void drawArc(float left, float top, float right, float bottom, float startAngle, float sweepAngle) {}
        @Override public void drawSector(float left, float top, float right, float bottom, float startAngle, float sweepAngle) {}
        @Override public void drawBitmap(int id, float left, float top, float right, float bottom) {}
        @Override public void drawCircle(float centerX, float centerY, float radius) {}
        @Override public void drawLine(float x1, float y1, float x2, float y2) {}
        @Override public void drawOval(float left, float top, float right, float bottom) {}
        @Override public void drawPath(int id, float start, float end) {}
        @Override public void drawRect(float left, float top, float right, float bottom) {}
        private float mCurrentTextSize = 16f;
        private final List<Float> mTextSizeStack = new ArrayList<>();
        private final PaintChanges mPaintChanges = new PaintChangeAdapter() {
            @Override
            public void setTextSize(float size) {
                mCurrentTextSize = size;
            }
        };

        @Override public void savePaint() {
            mTextSizeStack.add(mCurrentTextSize);
        }
        @Override public void restorePaint() {
            if (!mTextSizeStack.isEmpty()) {
                mCurrentTextSize = mTextSizeStack.remove(mTextSizeStack.size() - 1);
            }
        }
        @Override public void replacePaint(@NonNull PaintBundle paintBundle) {
            paintBundle.applyPaintChange(this, mPaintChanges);
        }
        @Override public void drawRoundRect(float left, float top, float right, float bottom, float radiusX, float radiusY) {}
        @Override public void drawTextOnPath(int textId, int pathId, float hOffset, float vOffset) {}
        @Override public @Nullable String getText(int id) {
            return mContext != null ? mContext.getText(id) : "";
        }
        @Override public void getTextBounds(int textId, int start, int end, int flags, float @NonNull [] bounds) {
            String str = getText(textId);
            if (str == null || str.isEmpty()) {
                bounds[0] = 0; bounds[1] = 0; bounds[2] = 0; bounds[3] = 0;
                return;
            }
            int s = Math.max(0, start);
            int e = (end < 0 || end > str.length()) ? str.length() : end;
            int count = Math.max(0, e - s);
            float fontSize = mCurrentTextSize > 0 ? mCurrentTextSize : 16f;
            bounds[0] = 0;
            bounds[1] = -0.8f * fontSize;
            bounds[2] = count * fontSize;
            bounds[3] = 0.2f * fontSize;
        }
        @Override public RcPlatformServices.@Nullable ComputedTextLayout layoutComplexText(
                int textId, int start, int end, int alignment, int overflow, int maxLines,
                float maxWidth, float maxHeight, float letterSpacing, float lineHeightAdd,
                float lineHeightMultiplier, int lineBreakStrategy, int hyphenationFrequency,
                int justificationMode, boolean useUnderline, boolean strikethrough, int flags) {
            String str = getText(textId);
            if (str == null) {
                return null;
            }
            int s = Math.max(0, start);
            int e = (end < 0 || end > str.length()) ? str.length() : end;
            String text = str.substring(s, e);
            float fontSize = mCurrentTextSize > 0 ? mCurrentTextSize : 16f;
            float charWidth = fontSize + letterSpacing;
            float mult = lineHeightMultiplier > 0 ? lineHeightMultiplier : 1.0f;
            float lineHeight = fontSize * mult + lineHeightAdd;

            int maxChars = maxWidth > 0 ? (int) Math.floor(maxWidth / charWidth) : Integer.MAX_VALUE;
            if (maxChars < 1) maxChars = 1;

            List<String> lines = new ArrayList<>();
            String[] paragraphs = text.split("\n", -1);
            for (String p : paragraphs) {
                if (p.isEmpty()) {
                    lines.add("");
                    continue;
                }
                String[] words = p.split(" ");
                StringBuilder curLine = new StringBuilder();
                for (String word : words) {
                    if (curLine.length() == 0) {
                        if (word.length() <= maxChars) {
                            curLine.append(word);
                        } else {
                            int offset = 0;
                            while (offset < word.length()) {
                                int chunk = Math.min(maxChars, word.length() - offset);
                                if (offset + chunk == word.length() && chunk < maxChars) {
                                    curLine.append(word.substring(offset));
                                } else {
                                    lines.add(word.substring(offset, offset + chunk));
                                }
                                offset += chunk;
                            }
                        }
                    } else {
                        if (curLine.length() + 1 + word.length() <= maxChars) {
                            curLine.append(" ").append(word);
                        } else {
                            lines.add(curLine.toString());
                            curLine.setLength(0);
                            if (word.length() <= maxChars) {
                                curLine.append(word);
                            } else {
                                int offset = 0;
                                while (offset < word.length()) {
                                    int chunk = Math.min(maxChars, word.length() - offset);
                                    if (offset + chunk == word.length() && chunk < maxChars) {
                                        curLine.append(word.substring(offset));
                                    } else {
                                        lines.add(word.substring(offset, offset + chunk));
                                    }
                                    offset += chunk;
                                }
                            }
                        }
                    }
                }
                if (curLine.length() > 0) {
                    lines.add(curLine.toString());
                }
            }

            int visibleLineCount = lines.size();
            if (maxLines > 0 && lines.size() > maxLines) {
                while (lines.size() > maxLines) {
                    lines.remove(lines.size() - 1);
                }
                if (overflow == 3 /* OVERFLOW_ELLIPSIS */) {
                    int lastIdx = lines.size() - 1;
                    String last = lines.get(lastIdx);
                    if (last.length() + 3 <= maxChars) {
                        lines.set(lastIdx, last + "...");
                    } else {
                        int keep = Math.max(0, maxChars - 3);
                        lines.set(lastIdx, last.substring(0, Math.min(last.length(), keep)) + "...");
                    }
                }
            }

            float maxW = 0f;
            for (String l : lines) {
                float w = l.length() * charWidth;
                if (w > maxW) maxW = w;
            }
            float totalH = lines.size() * lineHeight;

            int count = 0;
            for (int i = 0; i < lines.size(); i++) {
                if ((i + 1) * lineHeight <= maxHeight) {
                    count++;
                }
            }
            if (count == 0 && !lines.isEmpty() && maxHeight > 0) {
                count = 1;
            }
            visibleLineCount = Math.min(lines.size(), count);

            return new AhemComputedTextLayout(maxW, totalH, visibleLineCount, false);
        }
        @Override public void drawTextRun(int textID, int start, int end, int contextStart, int contextEnd, float x, float y, boolean rtl) {}
        @Override public void drawComplexText(RcPlatformServices.@Nullable ComputedTextLayout layout) {}
        @Override public void drawTweenPath(int path1Id, int path2Id, float tween, float start, float stop) {}
        @Override public void tweenPath(int out, int path1, int path2, float tween) {}
        @Override public void combinePath(int out, int path1, int path2, byte operation) {}
        @Override public void applyPaint(@NonNull PaintBundle mPaintData) {
            mPaintData.applyPaintChange(this, mPaintChanges);
        }
        @Override public void matrixScale(float scaleX, float scaleY, float centerX, float centerY) {}
        @Override public void matrixTranslate(float translateX, float translateY) {}
        @Override public void matrixSkew(float skewX, float skewY) {}
        @Override public void matrixRotate(float rotate, float pivotX, float pivotY) {}
        @Override public void matrixSave() {}
        @Override public void matrixRestore() {}
        @Override public void clipRect(float left, float top, float right, float bottom) {}
        @Override public void clipPath(int pathId, int regionOp) {}
        @Override public void roundedClipRect(float left, float top, float right, float bottom, float radiusX, float radiusY) {}
        @Override public void reset() {}
        @Override public void startGraphicsLayer(int w, int h) {}
        @Override public void setGraphicsLayer(@NonNull HashMap<Integer, Object> attributes) {}
        @Override public void endGraphicsLayer() {}
        @Override public void matrixFromPath(int pathId, float fraction, float vOffset, int flags) {}
        @Override public void drawToBitmap(int bitmapId, int mode, int color) {}
    }

    private static class AhemComputedTextLayout implements RcPlatformServices.ComputedTextLayout {
        private final float mWidth;
        private final float mHeight;
        private final int mVisibleLines;
        private final boolean mIsHyphenated;

        AhemComputedTextLayout(float width, float height, int visibleLines, boolean isHyphenated) {
            mWidth = width;
            mHeight = height;
            mVisibleLines = visibleLines;
            mIsHyphenated = isHyphenated;
        }

        @Override public float getWidth() { return mWidth; }
        @Override public float getHeight() { return mHeight; }
        @Override public int getVisibleLineCount() { return mVisibleLines; }
        @Override public boolean isHyphenatedText() { return mIsHyphenated; }
    }

    private static class HeadlessRemoteContext extends RemoteContext {
        final Map<Integer, Float> floatMap = new HashMap<>();
        final Map<Integer, Integer> intMap = new HashMap<>();
        final Map<Integer, Integer> colorMap = new HashMap<>();
        final Map<Integer, String> textMap = new HashMap<>();
        final Map<Integer, Object> objectMap = new HashMap<>();
        final Map<String, Integer> nameToId = new HashMap<>();
        final Map<Integer, String> idToName = new HashMap<>();

        HeadlessRemoteContext() {
            super();
            mPaintContext = new HeadlessPaintContext(this);
        }

        @Override public void loadPathData(int instanceId, int winding, float @NonNull [] floatPath) {}
        @Override public float @Nullable [] getPathData(int instanceId) { return null; }
        @Override public void loadVariableName(@NonNull String varName, int varId, int varType) {
            nameToId.put(varName, varId);
            idToName.put(varId, varName);
        }
        @Override public void loadColor(int id, int color) {
            colorMap.put(id, color);
            mRemoteComposeState.updateColor(id, color);
        }
        @Override public void setNamedColorOverride(@NonNull String colorName, int color) {}
        @Override public void setNamedStringOverride(@NonNull String stringName, @NonNull String value) {}
        @Override public void clearNamedStringOverride(@NonNull String stringName) {}
        @Override public void setNamedBooleanOverride(@NonNull String booleanName, boolean value) {}
        @Override public void clearNamedBooleanOverride(@NonNull String booleanName) {}
        @Override public void setNamedIntegerOverride(@NonNull String integerName, int value) {}
        @Override public void clearNamedIntegerOverride(@NonNull String integerName) {}
        @Override public void setNamedFloatOverride(@NonNull String floatName, float value) {}
        @Override public void clearNamedFloatOverride(@NonNull String floatName) {}
        @Override public void setNamedLong(@NonNull String name, long value) {}
        @Override public void setNamedDataOverride(@NonNull String dataName, @NonNull Object value) {}
        @Override public void clearNamedDataOverride(@NonNull String dataName) {}
        @Override public void addCollection(int id, androidx.compose.remote.core.operations.utilities.@NonNull ArrayAccess collection) {}
        @Override public void putDataMap(int id, androidx.compose.remote.core.operations.utilities.@NonNull DataMap map) {}
        @Override public androidx.compose.remote.core.operations.utilities.@Nullable DataMap getDataMap(int id) { return null; }
        @Override public void runAction(int id, @NonNull String metadata) {}
        @Override public void runNamedAction(int id, @Nullable Object value) {}
        @Override public void putObject(int id, @NonNull Object value) {
            objectMap.put(id, value);
            mRemoteComposeState.updateObject(id, value);
        }
        @Override public @Nullable Object getObject(int id) {
            Object o = objectMap.get(id);
            return o != null ? o : mRemoteComposeState.getObject(id);
        }
        @Override public void hapticEffect(int type) {}
        @Override public void loadBitmap(int imageId, short encoding, short type, int width, int height, byte @NonNull [] bitmap) {}
        @Override public void loadText(int id, @NonNull String text) { textMap.put(id, text); mRemoteComposeState.updateData(id, text); }
        @Override public @Nullable String getText(int id) { return textMap.get(id); }
        @Override
        public void setAnimationTime(float time) {
            super.setAnimationTime(time);
            loadFloat(RemoteContext.ID_ANIMATION_TIME, time);
            loadFloat(RemoteContext.ID_CONTINUOUS_SEC, time);
        }

        @Override public void loadFloat(int id, float value) {
            floatMap.put(id, value);
            mRemoteComposeState.updateFloat(id, value);
            ArrayList<androidx.compose.remote.core.VariableSupport> listeners = mRemoteComposeState.getListeners(id);
            if (listeners != null) {
                for (androidx.compose.remote.core.VariableSupport vs : listeners) {
                    vs.updateVariables(this);
                    if (vs instanceof androidx.compose.remote.core.operations.FloatExpression) {
                        ((androidx.compose.remote.core.operations.FloatExpression) vs).apply(this);
                    }
                }
            }
        }
        @Override public void overrideFloat(int id, float value) { floatMap.put(id, value); mRemoteComposeState.overrideFloat(id, value); }
        @Override public void loadInteger(int id, int value) { intMap.put(id, value); floatMap.put(id, (float) value); mRemoteComposeState.updateInteger(id, value); }
        @Override public void overrideInteger(int id, int value) { intMap.put(id, value); mRemoteComposeState.overrideInteger(id, value); }
        @Override public void overrideText(int id, int valueId) { textMap.put(id, textMap.get(valueId)); mRemoteComposeState.updateData(id, textMap.get(valueId)); }
        @Override public void loadAnimatedFloat(int id, androidx.compose.remote.core.operations.@NonNull FloatExpression animatedFloat) {
            mRemoteComposeState.cacheData(id, animatedFloat);
        }
        @Override public void loadShader(int id, androidx.compose.remote.core.operations.@NonNull ShaderData value) {}
        @Override public float getFloat(int id) {
            Float v = floatMap.get(id);
            return v != null ? v : mRemoteComposeState.getFloat(id);
        }
        @Override public int getInteger(int id) {
            Integer v = intMap.get(id);
            return v != null ? v : mRemoteComposeState.getInteger(id);
        }
        @Override public long getLong(int id) { return 0L; }
        @Override public int getColor(int id) {
            Integer v = colorMap.get(id);
            return v != null ? v : mRemoteComposeState.getColor(id);
        }
        /**
         * Whether an expression actually wrote a value for {@code id}, as opposed to the id
         * simply reading back as zero. Only the local maps are consulted: they record exactly
         * the {@code loadInteger} / {@code loadColor} calls the operations made.
         */
        boolean hasInteger(int id) { return intMap.containsKey(id); }
        boolean hasColor(int id) { return colorMap.containsKey(id); }
        @Override public void listensTo(int id, androidx.compose.remote.core.@NonNull VariableSupport variableSupport) {
            mRemoteComposeState.listenToVar(id, variableSupport);
        }
        @Override public @Nullable ArrayList<androidx.compose.remote.core.VariableSupport> getListeners(int id) {
            return mRemoteComposeState.getListeners(id);
        }
        @Override public int updateOps() { return 0; }
        @Override public androidx.compose.remote.core.operations.ShaderData getShader(int id) { return null; }
        @Override public void addClickArea(int id, int contentDescription, float left, float top, float right, float bottom, int metadataId) {}
    }

    private static class MockPlatform implements RcPlatformServices {
        @Override public float[] pathToFloatArray(Object path) { return new float[0]; }
        @Override public Object parsePath(String path) { return new Object(); }
        @Override public byte[] imageToByteArray(Object image) { return new byte[0]; }
        @Override public int getImageWidth(Object image) { return 0; }
        @Override public int getImageHeight(Object image) { return 0; }
        @Override public boolean isAlpha8Image(Object image) { return false; }
        @Override public void log(@NonNull LogCategory category, @NonNull String message) {}
    }

    private static void walk(Operation op, int depth, Set<Operation> seen, Map<Integer, ComponentInfo> found) {
        if (op == null || seen.contains(op) || depth > 24) return;
        seen.add(op);
        String kind = op.getClass().getSimpleName();
        if (op instanceof Component) {
            Component c = (Component) op;
            ComponentInfo info = new ComponentInfo(
                    c.getComponentId(), kind, depth, c.getX(), c.getY(), c.getWidth(), c.getHeight(),
                    c.getScrollX(), c.getScrollY()
            );
            info.isGone = c.isGone();
            info.visibility = c.isGone() ? "GONE" : "VISIBLE";
            found.put(c.getComponentId(), info);
            depth++;
        }
        if (op instanceof Container) {
            for (Operation child : ((Container) op).getList()) {
                walk(child, depth, seen, found);
            }
        }
    }

    /**
     * The size the reference host reports for the root component.
     *
     * <p>{@code remote-core} sets the root's size from two places that disagree:
     *
     * <ul>
     *   <li>{@code RootLayoutComponent.measure} finishes with {@code
     *       setWidth(firstComponent.getWidth())} (RootLayoutComponent.java:309-311) -- the root
     *       <em>wraps to its first child</em>.
     *   <li>{@code RootLayoutComponent.layout(RemoteContext)} finishes with {@code
     *       self.setW(context.mWidth)} (RootLayoutComponent.java:256-259) -- the root <em>fills the
     *       viewport</em>.
     * </ul>
     *
     * <p>Which one a host observes depends on its measure/paint call order and on how far its clock
     * has moved, because the transition between the two is animated: replaying these documents
     * through {@code CoreDocument} directly caught the root at 121.38x80.92, in flight between the
     * two answers. This harness holds the clock still across warm-up frames on purpose -- they are
     * repeated measurements of one instant, not a passage of time -- so it used to record the
     * wrapped answer, and the corpus ended up asserting trees whose root was <em>smaller than its
     * own child</em>: a 120x80 root containing a 200x150 box, on all nine {@code state_layout_*}
     * golds.
     *
     * <p>{@code RemoteComposeView} -- the reference host -- reports the viewport, and that is what
     * the corpus records. This is a deliberate override of what the engine happens to leave in the
     * field, not a measurement, and it is scoped as narrowly as possible:
     *
     * <ul>
     *   <li>the root node only;
     *   <li>only when the document is not scaling its content (under {@code SIZING_SCALE} the root
     *       keeps the document's authored size and the host applies a transform instead, so the
     *       viewport would be the wrong answer);
     *   <li>only on <em>settled</em> captures. Animation frames sample the layout deliberately
     *       mid-flight, and there the view player shows the root animating too -- 100x100 at frame
     *       0 rising to 199.45x149.73 by frame 5, on its way to a 300x200 viewport. Overriding
     *       those would replace a real observation with a guess at where it was heading.
     * </ul>
     *
     * @param doc the document being measured
     * @param remote the context whose {@code mWidth}/{@code mHeight} hold the current viewport
     * @return the size to record for the root, or null to record what the engine left behind
     */
    private static float @Nullable [] hostRootSize(CoreDocument doc, RemoteContext remote) {
        if (doc.getContentSizing() == RootContentBehavior.SIZING_SCALE) {
            return null;
        }
        return new float[] {remote.mWidth, remote.mHeight};
    }

    /**
     * Extracts the layout tree as the engine currently holds it.
     *
     * <p>Used for captures that deliberately sample a layout in motion. See {@link
     * #extractSettledTree} for the settled case and {@link #hostRootSize} for why the two differ.
     */
    private static JSONArray extractTree(CoreDocument doc) {
        return extractTree(doc, null);
    }

    /**
     * Extracts the layout tree of a settled layout, with the root recorded the way the reference
     * host reports it. See {@link #hostRootSize}.
     */
    private static JSONArray extractSettledTree(CoreDocument doc, RemoteContext remote) {
        return extractTree(doc, hostRootSize(doc, remote));
    }

    private static JSONArray extractTree(CoreDocument doc, float @Nullable [] rootSize) {
        Map<Integer, ComponentInfo> found = new LinkedHashMap<>();
        Set<Operation> seen = new HashSet<>();
        for (Operation op : doc.getOperations()) {
            walk(op, 0, seen, found);
        }

        List<Integer> sortedIds = new ArrayList<>(found.keySet());
        Collections.sort(sortedIds);

        JSONArray expectedTreeArr = new JSONArray();
        for (int id : sortedIds) {
            ComponentInfo c = found.get(id);
            float w = c.w;
            float h = c.h;
            if (rootSize != null && "RootLayoutComponent".equals(c.kind)) {
                w = rootSize[0];
                h = rootSize[1];
            }
            JSONObject compObj = new JSONObject();
            compObj.put("id", c.id);
            compObj.put("kind", c.kind);
            compObj.put("depth", c.depth);
            compObj.put("x", Math.round(c.x * 100.0) / 100.0);
            compObj.put("y", Math.round(c.y * 100.0) / 100.0);
            compObj.put("width", Math.round(w * 100.0) / 100.0);
            compObj.put("height", Math.round(h * 100.0) / 100.0);
            if (c.scrollX != 0f) {
                compObj.put("scroll_x", Math.round(c.scrollX * 100.0) / 100.0);
            }
            if (c.scrollY != 0f) {
                compObj.put("scroll_y", Math.round(c.scrollY * 100.0) / 100.0);
            }
            compObj.put("isGone", c.isGone);
            compObj.put("visibility", c.visibility);
            expectedTreeArr.put(compObj);
        }
        return expectedTreeArr;
    }

    static class ClassCoverageStat {
        String subsystem;
        String name;
        String category;
        int coveredInstructions;
        int totalInstructions;
        int coveredLines;
        int totalLines;
        int coveredBranches;
        int totalBranches;

        double getInstructionPct() {
            return totalInstructions > 0 ? (coveredInstructions * 100.0 / totalInstructions) : 0.0;
        }

        double getLinePct() {
            return totalLines > 0 ? (coveredLines * 100.0 / totalLines) : 0.0;
        }

        double getBranchPct() {
            return totalBranches > 0 ? (coveredBranches * 100.0 / totalBranches) : 0.0;
        }
    }

    static class SubsystemCoverageReport {
        int coveredInstructions;
        int totalInstructions;
        int coveredLines;
        int totalLines;
        int coveredBranches;
        int totalBranches;
        List<ClassCoverageStat> classes = new ArrayList<>();

        double getInstructionPct() {
            return totalInstructions > 0 ? (coveredInstructions * 100.0 / totalInstructions) : 0.0;
        }

        double getLinePct() {
            return totalLines > 0 ? (coveredLines * 100.0 / totalLines) : 0.0;
        }

        double getBranchPct() {
            return totalBranches > 0 ? (coveredBranches * 100.0 / totalBranches) : 0.0;
        }
    }

    private static String categorizeClass(String name) {
        if (name.startsWith("operations/layout/managers/policies/")) return "Measure Policy";
        if (name.startsWith("operations/layout/managers/")) return "Layout Manager";
        if (name.startsWith("operations/layout/modifiers/")) return "Modifier";
        if (name.startsWith("operations/layout/measure/")) return "Measure Subsystem";
        if (name.startsWith("operations/layout/animation/")) return "Animation";
        if (name.startsWith("operations/paint/")) return "Paint Bundle";
        if (name.startsWith("operations/utilities/")) return "Expression Utility";
        if (name.startsWith("operations/loom/")) return "Loom / Macro";
        if (name.startsWith("operations/matrix/")) return "Matrix";
        if (name.startsWith("semantics/")) return "Semantics";
        if (name.startsWith("serialize/") || name.startsWith("documentation/")
                || name.startsWith("types/")) return "Serialization";
        if (name.startsWith("operations/")) return "Operation";
        return "Utility / Infrastructure";
    }

    /**
     * Maps a reference class onto the conformance subsystem that exercises it, so the
     * coverage table can be read alongside the per-subsystem pass rates. Classes that
     * no single subsystem owns (the document, the wire buffer, the op registry) land in
     * "core" rather than being silently attributed to whichever subsystem sorts first.
     */
    private static String subsystemForClass(String path) {
        // Package-level ownership first — it is unambiguous.
        if (path.startsWith("operations/layout/animation/")) return "animationspec";
        if (path.startsWith("operations/layout/")) return "layout";
        if (path.startsWith("operations/loom/")) return "loom";
        if (path.startsWith("operations/matrix/")) return "matrixmath";
        if (path.startsWith("operations/paint/")) return "canvas";
        if (path.startsWith("semantics/")) return "semantics";

        // Everything else is a flat operation class; match on the class name.
        String n = path.substring(path.lastIndexOf('/') + 1);
        int dollar = n.indexOf('$');
        if (dollar >= 0) n = n.substring(0, dollar);

        if (n.equals("ConditionalOperations") || n.equals("Skip")) return "conditionals";
        if (n.equals("DrawTextOnPath") || n.equals("DrawTextOnCircle")
                || n.equals("DrawTextAnchored") || n.equals("DrawBitmapFontTextOnPath")
                || n.equals("DrawBitmapTextAnchored")) return "canvas";
        if (n.startsWith("Matrix")) return "matrixmath";
        if (n.startsWith("Particles")) return "particles";
        if (n.startsWith("Color") || n.equals("Theme")) return "colortheme";
        if (n.startsWith("Data") || n.equals("UpdateDynamicFloatList")
                || n.equals("IdLookup")) return "dataoperations";
        if (n.startsWith("Path") || n.equals("DrawTweenPath")) return "pathoperations";
        if (n.equals("ShaderData")) return "shaders";
        if (n.equals("WakeIn") || n.startsWith("Impulse")) return "scheduling";
        if (n.endsWith("Clock") || n.equals("TimeVariables")
                || n.equals("TimeAttribute")) return "clock";
        if (n.equals("ClickArea") || n.equals("TouchExpression") || n.equals("HapticFeedback")
                || n.equals("PlaySound") || n.startsWith("Sound")) return "interactivity";
        if (n.startsWith("Text") || n.equals("FontData") || n.equals("BitmapFontData")
                || n.equals("BitmapTextMeasure")) return "textoperations";
        if (n.startsWith("Float") || n.equals("IntegerExpression") || n.equals("NamedVariable")
                || n.equals("Rem")) return "expressions";
        if (n.startsWith("Draw") || n.startsWith("Clip") || n.equals("PaintData")
                || n.equals("BitmapData") || n.equals("ImageAttribute")) return "canvas";
        if (n.equals("ReferencedOperations") || n.equals("IncludeReferencedOperations")
                || n.equals("Header") || n.equals("WireBuffer")
                || n.equals("RemoteComposeBuffer") || n.equals("RecordingRemoteComposeBuffer")) {
            return "wire";
        }
        // NanMap, expression evaluators and friends back the expression engine.
        if (path.startsWith("operations/utilities/")) return "expressions";
        return "core";
    }

    /**
     * Replays every gold document in the corpus through the reference engine so that coverage
     * reflects the whole conformance suite rather than only the three categories this class
     * happens to encode from JSON.
     *
     * <p>Only {@code document_base64} is used — no JSON document parsing — which is both why
     * this works for gold files authored outside this generator and why it mirrors what a real
     * player does. Without this pass, subsystems whose corpus is authored elsewhere (matrix
     * math, text-on-path, conditionals, …) report 0% coverage even though they are well tested.
     *
     * @return number of documents successfully played
     */
    private static int replayGoldCorpus(File conformanceDir) {
        File goldRoot = new File(conformanceDir, "gold");
        File[] categories = goldRoot.listFiles(File::isDirectory);
        if (categories == null) return 0;

        int played = 0;
        List<String> unplayable = new ArrayList<>();
        for (File category : categories) {
            File[] golds = category.listFiles((d, n) -> n.endsWith(".gold.json"));
            if (golds == null) continue;
            for (File gf : golds) {
                try {
                    byte[] raw = new byte[(int) gf.length()];
                    try (FileInputStream fis = new FileInputStream(gf)) {
                        fis.read(raw);
                    }
                    JSONObject gold = new JSONObject(new String(raw, StandardCharsets.UTF_8));
                    String b64 = gold.optString("document_base64", "");
                    if (b64.isEmpty()) continue;
                    byte[] rcBytes = Base64.getDecoder().decode(b64);

                    JSONObject params = gold.optJSONObject("parameters");
                    int width = params != null ? params.optInt("width", 400) : 400;
                    int height = params != null ? params.optInt("height", 400) : 400;

                    RemoteComposeBuffer buffer =
                            RemoteComposeBuffer.fromInputStream(new ByteArrayInputStream(rcBytes));
                    CoreDocument doc = new CoreDocument();
                    doc.initFromBuffer(buffer);

                    HeadlessRemoteContext remote = new HeadlessRemoteContext();
                    remote.mWidth = width;
                    remote.mHeight = height;
                    doc.initializeContext(remote);
                    remote.loadFloat(5, width);
                    remote.loadFloat(6, height);
                    doc.applyDataOperations(remote);

                    // A couple of frames so time-dependent and animated paths are exercised.
                    for (int f = 0; f < 3; f++) {
                        remote.setAnimationTime(f / 60.0f);
                        remote.loadFloat(RemoteContext.ID_ANIMATION_TIME, f / 60.0f);
                        doc.measure(remote, 0, width, 0, height);
                        doc.paint(remote, Theme.UNSPECIFIED);
                    }
                    played++;
                } catch (Throwable t) {
                    // A document the reference engine cannot play is itself interesting, but it
                    // must not abort coverage collection.
                    String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                    unplayable.add(category.getName() + "/" + gf.getName().replace(".gold.json", "")
                            + " :: " + why.replace('\n', ' ').trim());
                }
            }
        }
        System.out.println("Coverage replay: played " + played + " gold documents ("
                + unplayable.size() + " could not be played)");
        if (!unplayable.isEmpty()) {
            System.out.println("==== gold documents the reference engine could not play ====");
            for (String s : unplayable) {
                System.out.println("  - " + s);
            }
        }
        return played;
    }

    private static SubsystemCoverageReport computeCoverage(File conformanceDir) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("org.jacoco:type=Runtime");
            if (mbs.isRegistered(name)) {
                mbs.invoke(name, "dump", new Object[]{false}, new String[]{"boolean"});
            }
        } catch (Throwable ignored) {}

        File execFile = new File("/tmp/jacoco.exec");
        File serverDir = new File("/Users/nicolasroard/.jetski/extensions/vscjava.vscode-java-test-0.46.0-universal/server");
        File classesDir = new File("/Users/nicolasroard/androidx-main-secondary/out/androidx/compose/remote/remote-core/build/classes/java/main/androidx/compose/remote/core");

        if (!execFile.exists() || !serverDir.exists() || !classesDir.exists()) {
            return null;
        }

        File[] jars = serverDir.listFiles((d, n) -> n.startsWith("org.jacoco.core") || n.startsWith("org.objectweb.asm"));
        if (jars == null || jars.length == 0) {
            return null;
        }

        try {
            URL[] urls = new URL[jars.length];
            for (int i = 0; i < jars.length; i++) {
                urls[i] = jars[i].toURI().toURL();
            }
            try (URLClassLoader cl = new URLClassLoader(urls, ConformanceGoldGeneratorTest.class.getClassLoader())) {
                Class<?> execFileLoaderClass = cl.loadClass("org.jacoco.core.tools.ExecFileLoader");
                Object loader = execFileLoaderClass.getConstructor().newInstance();
                execFileLoaderClass.getMethod("load", File.class).invoke(loader, execFile);

                Class<?> coverageBuilderClass = cl.loadClass("org.jacoco.core.analysis.CoverageBuilder");
                Object builder = coverageBuilderClass.getConstructor().newInstance();

                Class<?> dataStoreClass = cl.loadClass("org.jacoco.core.data.ExecutionDataStore");
                Object store = execFileLoaderClass.getMethod("getExecutionDataStore").invoke(loader);

                Class<?> coverageVisitorClass = cl.loadClass("org.jacoco.core.analysis.ICoverageVisitor");
                Class<?> analyzerClass = cl.loadClass("org.jacoco.core.analysis.Analyzer");
                Object analyzer = analyzerClass.getConstructor(dataStoreClass, coverageVisitorClass).newInstance(store, builder);

                analyzerClass.getMethod("analyzeAll", File.class).invoke(analyzer, classesDir);

                Collection<?> classCoverages = (Collection<?>) coverageBuilderClass.getMethod("getClasses").invoke(builder);
                SubsystemCoverageReport report = new SubsystemCoverageReport();

                for (Object cc : classCoverages) {
                    String rawName = (String) cc.getClass().getMethod("getName").invoke(cc);
                    String simpleName = rawName.replace("androidx/compose/remote/core/", "");

                    Object inst = cc.getClass().getMethod("getInstructionCounter").invoke(cc);
                    Object lines = cc.getClass().getMethod("getLineCounter").invoke(cc);
                    Object branches = cc.getClass().getMethod("getBranchCounter").invoke(cc);

                    int cInst = (Integer) inst.getClass().getMethod("getCoveredCount").invoke(inst);
                    int tInst = (Integer) inst.getClass().getMethod("getTotalCount").invoke(inst);
                    int cLines = (Integer) lines.getClass().getMethod("getCoveredCount").invoke(lines);
                    int tLines = (Integer) lines.getClass().getMethod("getTotalCount").invoke(lines);
                    int cBranches = (Integer) branches.getClass().getMethod("getCoveredCount").invoke(branches);
                    int tBranches = (Integer) branches.getClass().getMethod("getTotalCount").invoke(branches);

                    report.coveredInstructions += cInst;
                    report.totalInstructions += tInst;
                    report.coveredLines += cLines;
                    report.totalLines += tLines;
                    report.coveredBranches += cBranches;
                    report.totalBranches += tBranches;

                    ClassCoverageStat stat = new ClassCoverageStat();
                    stat.name = simpleName;
                    stat.category = categorizeClass(simpleName);
                    stat.subsystem = subsystemForClass(simpleName);
                    stat.coveredInstructions = cInst;
                    stat.totalInstructions = tInst;
                    stat.coveredLines = cLines;
                    stat.totalLines = tLines;
                    stat.coveredBranches = cBranches;
                    stat.totalBranches = tBranches;
                    report.classes.add(stat);
                }

                report.classes.sort(Comparator.comparing(s -> s.name));
                return report;
            }
        } catch (Throwable t) {
            System.err.println("Notice: JaCoCo coverage analysis could not run: " + t.getMessage());
            return null;
        }
    }

    /**
     * Writes the JaCoCo coverage figures that {@code generate-gold-overview.mjs} renders onto
     * the gold overview page.
     *
     * <p>Coverage is the only part of that page this class can produce: the numbers come from
     * the JaCoCo runtime attached to this JVM, which a Node script has no way to reach. The
     * corpus inventory and the HTML itself are produced by {@code generate-gold-overview.mjs},
     * which walks every gold in every subsystem.
     *
     * <p>This class used to render the page too, from the {@code GoldRecord}s it collected
     * while generating. That could only ever describe the layout subsystem — the generation
     * loop reads {@code tests/layout} and nothing else — so the page it wrote covered a
     * fraction of the corpus and had to be overwritten by the Node generator afterwards.
     * Whichever ran last won, silently. Emitting data and leaving the rendering to one place
     * removes that ordering hazard.
     */
    private static void writeCoverageReport(
            File conformanceDir,
            SubsystemCoverageReport coverage) throws Exception {

        if (coverage == null) {
            System.out.println("No JaCoCo coverage available; coverage.json not written.");
            return;
        }

        File conformanceFolder = conformanceDir;
        if (!conformanceFolder.exists()) {
            conformanceFolder.mkdirs();
        }

        JSONObject covObj = new JSONObject();
        covObj.put("generated_at", Instant.now().toString());
        covObj.put("subsystem", "androidx.compose.remote.core");
        covObj.put("instruction_coverage_pct", Math.round(coverage.getInstructionPct() * 100.0) / 100.0);
        covObj.put("covered_instructions", coverage.coveredInstructions);
        covObj.put("total_instructions", coverage.totalInstructions);
        covObj.put("line_coverage_pct", Math.round(coverage.getLinePct() * 100.0) / 100.0);
        covObj.put("covered_lines", coverage.coveredLines);
        covObj.put("total_lines", coverage.totalLines);
        covObj.put("branch_coverage_pct", Math.round(coverage.getBranchPct() * 100.0) / 100.0);
        covObj.put("covered_branches", coverage.coveredBranches);
        covObj.put("total_branches", coverage.totalBranches);

        JSONArray classesArr = new JSONArray();
        for (ClassCoverageStat s : coverage.classes) {
            JSONObject co = new JSONObject();
            co.put("name", s.name);
            co.put("category", s.category);
            co.put("subsystem", s.subsystem);
            co.put("instructions_pct", Math.round(s.getInstructionPct() * 100.0) / 100.0);
            co.put("instructions_covered", s.coveredInstructions);
            co.put("instructions_total", s.totalInstructions);
            co.put("lines_pct", Math.round(s.getLinePct() * 100.0) / 100.0);
            co.put("lines_covered", s.coveredLines);
            co.put("lines_total", s.totalLines);
            co.put("branches_pct", Math.round(s.getBranchPct() * 100.0) / 100.0);
            co.put("branches_covered", s.coveredBranches);
            co.put("branches_total", s.totalBranches);
            classesArr.put(co);
        }
        covObj.put("classes", classesArr);

        // Per-conformance-subsystem roll-up, so every subsystem gets a coverage number
        // instead of layout being the only one with any.
        Map<String, int[]> bySub = new LinkedHashMap<>();
        for (ClassCoverageStat s2 : coverage.classes) {
            int[] acc = bySub.computeIfAbsent(s2.subsystem, k -> new int[6]);
            acc[0] += s2.coveredInstructions;
            acc[1] += s2.totalInstructions;
            acc[2] += s2.coveredLines;
            acc[3] += s2.totalLines;
            acc[4] += s2.coveredBranches;
            acc[5] += s2.totalBranches;
        }
        JSONObject bySubObj = new JSONObject();
        for (Map.Entry<String, int[]> e : bySub.entrySet()) {
            int[] a = e.getValue();
            JSONObject so = new JSONObject();
            so.put("covered_instructions", a[0]);
            so.put("total_instructions", a[1]);
            so.put("instruction_coverage_pct", a[1] > 0 ? Math.round(a[0] * 10000.0 / a[1]) / 100.0 : 0.0);
            so.put("covered_lines", a[2]);
            so.put("total_lines", a[3]);
            so.put("line_coverage_pct", a[3] > 0 ? Math.round(a[2] * 10000.0 / a[3]) / 100.0 : 0.0);
            so.put("covered_branches", a[4]);
            so.put("total_branches", a[5]);
            so.put("branch_coverage_pct", a[5] > 0 ? Math.round(a[4] * 10000.0 / a[5]) / 100.0 : 0.0);
            bySubObj.put(e.getKey(), so);
        }
        covObj.put("by_subsystem", bySubObj);

        File covOut = new File(conformanceFolder, "coverage.json");
        try (FileOutputStream fos = new FileOutputStream(covOut)) {
            fos.write(covObj.toString(2).getBytes(StandardCharsets.UTF_8));
        }

        System.out.println();
        System.out.println("================================================================================");
        System.out.println("RemoteCompose Reference Engine Coverage");
        System.out.println("================================================================================");
        System.out.printf(Locale.US, "  Instruction Coverage: %.2f%% (%,d / %,d instructions)%n",
                coverage.getInstructionPct(), coverage.coveredInstructions, coverage.totalInstructions);
        System.out.printf(Locale.US, "  Line Coverage:        %.2f%% (%,d / %,d lines)%n",
                coverage.getLinePct(), coverage.coveredLines, coverage.totalLines);
        System.out.printf(Locale.US, "  Branch Coverage:      %.2f%% (%,d / %,d branches)%n",
                coverage.getBranchPct(), coverage.coveredBranches, coverage.totalBranches);
        System.out.println();
        System.out.println("Coverage JSON: " + covOut.getPath());
        System.out.println("Render the overview with: node generate-gold-overview.mjs");
        System.out.println("================================================================================");
    }

// ============================================================================================
    // format_version 2 emission.
    //
    // A gold's portable form — `timeline` + `checks` — is the only assertion vocabulary a
    // player sees. It used to be derived after the fact by a separate script reading a parallel
    // set of `expected_*` keys, which made every regeneration a two-step operation: this test
    // rewrote each gold wholesale, dropping the v2 blocks, and the script had to be run again
    // to put them back. Forgetting the second step left golds the runner would skip.
    //
    // Deriving v2 here removes the second step and, with it, the second representation. The
    // `expected_*` values assembled by the generator methods above are now scratch input to the
    // derivation below; `writeGold` strips them, so they never reach disk.
    // ============================================================================================

    /** Probes that are only meaningful to a player implementing the extended profile. */
    private static final Set<String> EXTENDED_PROBES = new HashSet<>(Arrays.asList("trace", "relation"));

    /**
     * Attaches `format_version`, `profile`, `harness`, `timeline` and `checks` to an assembled
     * gold, and asserts that every check names a step that exists.
     *
     * <p>A check whose `at` names no step never runs, which from the outside is indistinguishable
     * from a check that passes — the silent-pass mode this format exists to remove. It is worth
     * failing the generator over.
     */
    private static void attachUnifiedFormat(JSONObject goldJson, String category) {
        JSONArray timeline = new JSONArray();
        JSONArray checks = new JSONArray();

        switch (category) {
            case "layout":
                deriveLayoutTimeline(goldJson, timeline, checks);
                break;
            case "expressions":
                deriveExpressionsTimeline(goldJson, timeline, checks);
                break;
            case "particles":
                deriveParticlesTimeline(goldJson, timeline, checks);
                break;
            default:
                throw new IllegalArgumentException("No v2 derivation for category: " + category);
        }

        goldJson.put("category", category);
        goldJson.put("format_version", 2);

        boolean extended = false;
        for (int i = 0; i < checks.length(); i++) {
            if (EXTENDED_PROBES.contains(checks.getJSONObject(i).optString("probe"))) {
                extended = true;
                break;
            }
        }
        goldJson.put("profile", extended ? "extended" : "core");

        // Which text *measurement* model produced this gold's geometry. Since the corpus pins
        // the rendered typeface to Ahem on every engine, this no longer says anything about
        // which font was drawn — only about how the sizes in `checks` were arrived at:
        //
        //   "ahem"    this generator's closed-form model — one em of advance per glyph, ascent
        //             0.8em, descent 0.2em, greedy word wrap on character counts. It matches
        //             Ahem's design, so the numbers are reproducible by any engine that
        //             implements the same arithmetic, with no font engine required. A player
        //             replaying such a gold must use the same model, or its tree will not match.
        //
        //   "native"  measured by the platform's real text stack against the Ahem face. Metrics
        //             agree with the model above for simple runs, but line breaking does not:
        //             a real layout engine breaks differently from greedy character counting.
        //             That difference is the remaining source of disagreement on the multi-line
        //             `core_text_*` golds, whose trees are "ahem" while their rasters are
        //             captured natively.
        JSONObject harness = new JSONObject();
        harness.put("text_metrics", "layout".equals(category) ? "ahem" : "native");
        goldJson.put("harness", harness);

        goldJson.put("timeline", timeline);
        goldJson.put("checks", checks);

        List<String> problems = validateChecks(timeline, checks);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Gold " + goldJson.optString("name") + " has invalid checks: " + problems);
        }
    }

    /**
     * Every step id a check may legally name: the explicit {@code id} of each timeline step,
     * plus the synthetic {@code frame_N} ids a {@code frame_sequence} step expands into.
     */
    private static Set<String> timelineStepIds(JSONArray timeline) {
        Set<String> ids = new HashSet<>();
        if (timeline == null) {
            return ids;
        }
        for (int i = 0; i < timeline.length(); i++) {
            JSONObject step = timeline.getJSONObject(i);
            ids.add(step.getString("id"));
            if ("frame_sequence".equals(step.optString("kind"))) {
                JSONArray capture = step.optJSONArray("capture");
                for (int j = 0; capture != null && j < capture.length(); j++) {
                    ids.add("frame_" + capture.get(j));
                }
            }
        }
        return ids;
    }

    /** Every check must name a step that exists, and must carry an expected value. */
    private static List<String> validateChecks(JSONArray timeline, JSONArray checks) {
        Set<String> ids = timelineStepIds(timeline);
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < checks.length(); i++) {
            JSONObject c = checks.getJSONObject(i);
            if (!ids.contains(c.optString("at"))) {
                problems.add("check " + c.optString("probe") + " -> unknown step \"" + c.optString("at") + "\"");
            }
            if (!c.has("expect")) {
                problems.add("check " + c.optString("probe") + " at \"" + c.optString("at") + "\" has no expect");
            }
        }
        return problems;
    }

    private static double toleranceOf(JSONObject goldJson, double fallback) {
        JSONObject params = goldJson.optJSONObject("parameters");
        return params != null ? params.optDouble("tolerance", fallback) : fallback;
    }

    private static JSONObject treeCheck(String at, Object expect, double tolerance) {
        JSONObject c = new JSONObject();
        c.put("at", at);
        c.put("probe", "tree");
        c.put("expect", expect);
        c.put("tolerance", tolerance);
        return c;
    }

    /** Layout is the only subsystem with a non-trivial timeline: resizes, animation, gestures. */
    private static void deriveLayoutTimeline(JSONObject goldJson, JSONArray timeline, JSONArray checks) {
        JSONObject params = goldJson.optJSONObject("parameters");
        if (params == null) {
            params = new JSONObject();
        }
        double tol = toleranceOf(goldJson, 0.5);
        int frames = params.optInt("frames", 2);

        JSONObject initial = new JSONObject();
        initial.put("id", "initial");
        initial.put("kind", "paint");
        initial.put("frames", frames);
        timeline.put(initial);

        JSONArray resizeSteps = goldJson.optJSONArray("expected_resize_steps");
        JSONArray animFrames = goldJson.optJSONArray("expected_frames");
        JSONArray interactions = goldJson.optJSONArray("expected_interactions");

        if (resizeSteps != null) {
            for (int i = 0; i < resizeSteps.length(); i++) {
                JSONObject step = resizeSteps.getJSONObject(i);
                String id = "resize_" + step.getInt("step");
                JSONObject t = new JSONObject();
                t.put("id", id);
                t.put("kind", "resize");
                t.put("width", step.get("width"));
                t.put("height", step.get("height"));
                t.put("frames", frames);
                // The reference disables animation for the resize sweep, and only for the
                // sweep: the initial frames above run with it on. The flag therefore belongs
                // on these steps, not on `initial`. Placing it on `initial` -- as this
                // derivation used to -- silently asks a replaying player to freeze
                // AnimatableValue a step early, which changes the measured size of every
                // component whose dimensions animate. Ten StateLayout golds looked
                // irreproducible for exactly that reason.
                t.put("animation_enabled", false);
                t.put("label", step.opt("label"));
                timeline.put(t);
                checks.put(treeCheck(id, step.get("tree"), tol));
            }
        } else if (animFrames != null) {
            JSONObject anim = params.optJSONObject("animation");
            if (anim == null) {
                anim = new JSONObject();
            }
            if (anim.has("trigger")) {
                JSONObject t = new JSONObject();
                t.put("id", "trigger");
                t.put("kind", "trigger");
                t.put("trigger", anim.get("trigger"));
                t.put("capture", false);
                timeline.put(t);
            }
            JSONArray capture = new JSONArray();
            for (int i = 0; i < animFrames.length(); i++) {
                capture.put(animFrames.getJSONObject(i).get("frame"));
            }
            JSONObject seq = new JSONObject();
            seq.put("id", "animate");
            seq.put("kind", "frame_sequence");
            seq.put("total_frames", anim.optInt("total_frames", 30));
            seq.put("base_time_millis", BASE_TIME_MILLIS);
            seq.put("capture", capture);
            timeline.put(seq);

            for (int i = 0; i < animFrames.length(); i++) {
                JSONObject f = animFrames.getJSONObject(i);
                checks.put(treeCheck("frame_" + f.get("frame"), f.get("tree"), tol));
            }
        } else if (interactions != null) {
            initial.put("animation_time_seconds", 10.0);
            JSONArray declared = params.optJSONArray("interactions");
            for (int i = 0; i < interactions.length(); i++) {
                JSONObject act = interactions.getJSONObject(i);
                int stepIndex = act.getInt("step");
                JSONObject src = (declared != null && stepIndex < declared.length())
                        ? declared.getJSONObject(stepIndex) : new JSONObject();
                String id = "step_" + stepIndex;
                JSONObject t = new JSONObject();
                t.put("id", id);
                t.put("kind", act.getString("action"));
                t.put("label", act.has("label") ? act.get("label") : act.getString("action"));
                if (src.has("x")) {
                    t.put("x", src.get("x"));
                }
                if (src.has("y")) {
                    t.put("y", src.get("y"));
                }
                if (src.has("dx")) {
                    t.put("dx", src.get("dx"));
                }
                if (src.has("dy")) {
                    t.put("dy", src.get("dy"));
                }
                if (src.has("time_millis")) {
                    t.put("advance_millis", src.get("time_millis"));
                } else if (src.has("delta_millis")) {
                    t.put("advance_millis", src.get("delta_millis"));
                }
                timeline.put(t);
                checks.put(treeCheck(id, act.get("tree"), tol));
            }
        }

        // The initial layout is a separately-observable state, so assert it whenever it was
        // recorded — including alongside a resize sweep, animation or gesture sequence. The
        // legacy runner only asserted it in the plain case, which left most of the corpus
        // carrying a reference tree that nothing compared against.
        JSONArray tree = goldJson.optJSONArray("expected_tree");
        if (tree != null && tree.length() > 0) {
            boolean alreadyAsserted = false;
            for (int i = 0; i < checks.length(); i++) {
                JSONObject c = checks.getJSONObject(i);
                if ("tree".equals(c.optString("probe")) && "initial".equals(c.optString("at"))) {
                    alreadyAsserted = true;
                    break;
                }
            }
            if (!alreadyAsserted) {
                checks.put(treeCheck("initial", tree, tol));
            }
        }

        if (goldJson.has("gold_image_base64")) {
            JSONObject c = new JSONObject();
            c.put("at", "initial");
            c.put("probe", "raster");
            c.put("expect", goldJson.get("gold_image_base64"));
            // A raster tolerance is a count of non-antialiased differing pixels, not an RMSE.
            // Default documented in CONFORMANCE_FORMAT.md §2.4 and justified in
            // AndroidGoldGeneratorTest.DEFAULT_RASTER_TOLERANCE, which stamps the live corpus.
            c.put("tolerance", params.optInt("max_raster_pixels", 16));
            checks.put(c);
        }
    }

    /**
     * A target is either a numeric variable id or a variable name. Expressions address variables
     * by name; keeping both in one field avoids a second addressing mode in the format.
     */
    private static Object normalizeTarget(String key) {
        try {
            String trimmed = key.trim();
            double n = Double.parseDouble(trimmed);
            // Only treat it as numeric if it round-trips, matching the script's Number() test.
            if (String.valueOf((int) n).equals(trimmed)) {
                return (int) n;
            }
            if (String.valueOf(n).equals(trimmed)) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // Not numeric: it is a name.
        }
        return key;
    }

    /**
     * Reads back the expressions a test declares, keyed by the id the test gave them. Unlike
     * float variables these have no name in the context, so the ids can only come from the test
     * document itself.
     *
     * <p>An id the engine never wrote is <em>skipped</em>, not recorded as zero. Zero is a
     * perfectly legal colour and integer, so fabricating one would silently replace a correct
     * expectation with a wrong one that still looks authoritative.
     */
    private static JSONObject captureDeclaredIds(
            JSONObject docObj, String arrayKey, HeadlessRemoteContext remote, boolean asColor) {
        JSONObject out = new JSONObject();
        JSONArray declared = docObj.optJSONArray(arrayKey);
        for (int i = 0; declared != null && i < declared.length(); i++) {
            int id = declared.getJSONObject(i).optInt("id", -1);
            if (id < 0) continue;
            if (asColor ? !remote.hasColor(id) : !remote.hasInteger(id)) {
                System.out.println("  NOT OBSERVED: " + arrayKey + " id " + id
                        + " was declared but the engine wrote no value for it");
                continue;
            }
            out.put(String.valueOf(id), asColor ? remote.getColor(id) : remote.getInteger(id));
        }
        return out;
    }

    /** Keys in a stable order: numerically where they are ids, alphabetically where they are names. */
    private static List<String> sortedKeys(JSONObject obj) {
        List<String> keys = new ArrayList<>(obj.keySet());
        Collections.sort(keys, (a, b) -> {
            boolean na = a.matches("-?\\d+");
            boolean nb = b.matches("-?\\d+");
            if (na && nb) return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            if (na != nb) return na ? -1 : 1;
            return a.compareTo(b);
        });
        return keys;
    }

    /** Renders a time in seconds for use in a step id: {@code 0.25} → {@code "0.25"}, {@code 1.0} → {@code "1"}. */
    private static String timeLabel(double seconds) {
        String s = String.format(Locale.US, "%.4f", seconds);
        s = s.replaceAll("0+$", "");
        s = s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
        return s.isEmpty() ? "0" : s;
    }

    private static void deriveExpressionsTimeline(JSONObject goldJson, JSONArray timeline, JSONArray checks) {
        JSONObject params = goldJson.optJSONObject("parameters");
        double tol = toleranceOf(goldJson, 0.001);

        JSONObject initial = new JSONObject();
        initial.put("id", "initial");
        initial.put("kind", "paint");
        initial.put("frames", params != null ? params.optInt("frames", 1) : 1);
        // Non-layout documents are not laid out: a measure pass would descend into conditional
        // branches that paint skips.
        initial.put("measure", false);
        timeline.put(initial);

        valueChecks(goldJson.optJSONObject("expected_variables"), "float", "initial", tol, checks);
        // Integers and colours are exact: a tolerance on a bit mask or a packed ARGB word would
        // let neighbouring values pass.
        valueChecks(goldJson.optJSONObject("expected_integers"), "int", "initial", null, checks);
        valueChecks(goldJson.optJSONObject("expected_colors"), "color", "initial", null, checks);

        // Each declared animation sample becomes a step that parks the clock at an absolute
        // time, so the gold asserts the shape of a curve rather than a single instant.
        JSONArray steps = goldJson.optJSONArray("expected_steps");
        for (int i = 0; steps != null && i < steps.length(); i++) {
            JSONObject sample = steps.getJSONObject(i);
            double seconds = sample.getDouble("time");
            String id = "t_" + timeLabel(seconds);

            JSONObject step = new JSONObject();
            step.put("id", id);
            step.put("kind", "time");
            step.put("seconds", seconds);
            step.put("measure", false);
            timeline.put(step);

            valueChecks(sample.optJSONObject("variables"), "float", id, tol, checks);
        }
    }

    /** Emits one check per entry of {@code values}, in a stable order. */
    private static void valueChecks(
            JSONObject values, String probe, String at, Double tolerance, JSONArray checks) {
        if (values == null) return;
        for (String key : sortedKeys(values)) {
            JSONObject c = new JSONObject();
            c.put("at", at);
            c.put("probe", probe);
            c.put("target", normalizeTarget(key));
            c.put("expect", values.get(key));
            if (tolerance != null) c.put("tolerance", tolerance.doubleValue());
            checks.put(c);
        }
    }

    /** Particles carry their own frame sequence plus a settled terminal state. */
    private static void deriveParticlesTimeline(JSONObject goldJson, JSONArray timeline, JSONArray checks) {
        JSONObject params = goldJson.optJSONObject("parameters");
        double tol = toleranceOf(goldJson, 0.05);

        JSONObject initial = new JSONObject();
        initial.put("id", "initial");
        initial.put("kind", "paint");
        initial.put("frames", params != null ? params.optInt("frames", 1) : 1);
        initial.put("measure", false);
        timeline.put(initial);

        JSONArray frames = goldJson.optJSONArray("expected_frames");
        if (frames != null) {
            // `parameters.frames` is the simulation length, not a warm-up count. The sequence
            // starts from the un-painted document, so the initial step must not advance the
            // simulation or frame 0 would already be frame N.
            initial.put("frames", 0);
            JSONArray capture = new JSONArray();
            for (int i = 0; i < frames.length(); i++) {
                capture.put(frames.getJSONObject(i).get("frame"));
            }
            JSONObject seq = new JSONObject();
            seq.put("id", "animate");
            seq.put("kind", "frame_sequence");
            seq.put("total_frames", params != null ? params.optInt("frames", 5) : 5);
            seq.put("inclusive", true);
            seq.put("capture", capture);
            timeline.put(seq);

            for (int i = 0; i < frames.length(); i++) {
                JSONObject f = frames.getJSONObject(i);
                JSONObject c = new JSONObject();
                c.put("at", "frame_" + f.get("frame"));
                c.put("probe", "particles");
                c.put("expect", f.has("particles") ? f.get("particles") : f);
                c.put("tolerance", tol);
                checks.put(c);
            }
        }

        if (goldJson.has("final_particles")) {
            // The terminal state is whatever the last frame left behind, but a check has to name
            // a step that exists, so it is declared explicitly.
            JSONObject settle = new JSONObject();
            settle.put("id", "final");
            settle.put("kind", "settle");
            timeline.put(settle);

            JSONObject c = new JSONObject();
            c.put("at", "final");
            c.put("probe", "particles");
            c.put("expect", goldJson.get("final_particles"));
            c.put("tolerance", tol);
            checks.put(c);
        }
    }

    private static File findConformanceDir(File search) {
        for (int i = 0; i < 5 && search != null; i++) {
            File candidate = new File(search, "specification/conformance/tests/layout");
            if (candidate.exists()) {
                return new File(search, "specification/conformance");
            }
            candidate = new File(search, "compose/remote/specification/conformance/tests/layout");
            if (candidate.exists()) {
                return new File(search, "compose/remote/specification/conformance");
            }
            candidate = new File(search, "conformance/tests/layout");
            if (candidate.exists()) {
                return new File(search, "conformance");
            }
            candidate = new File(search, "compose/remote/conformance/tests/layout");
            if (candidate.exists()) {
                return new File(search, "compose/remote/conformance");
            }
            search = search.getParentFile();
        }
        return null;
    }

    @Test
    public void generateLayoutConformanceGoldFiles() throws Exception {
        // Find the conformance directory
        File currentDir = new File(".").getCanonicalFile();
        File conformanceDir = findConformanceDir(currentDir);

        assertNotNull("Could not find conformance directory from " + currentDir, conformanceDir);

        File testsDir = new File(conformanceDir, "tests/layout");
        File goldDir = new File(conformanceDir, "gold/layout");
        if (!goldDir.exists()) {
            goldDir.mkdirs();
        }

        File[] testFiles = testsDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(testFiles);
        assertTrue(testFiles.length > 0);

        int processed = 0;
        // Tests the Java-side RemoteComposeJsonParser cannot express. We record and report
        // these rather than either crashing the run or silently overwriting a hand-authored
        // gold file with a degenerate one.
        List<String> unsupported = new ArrayList<>();
        for (File tf : testFiles) {
            String content;
            try (FileInputStream fis = new FileInputStream(tf)) {
                byte[] b = new byte[(int) tf.length()];
                fis.read(b);
                content = new String(b, StandardCharsets.UTF_8);
            }

            JSONObject testJson = new JSONObject(content);
            String name = testJson.getString("name");
            String description = testJson.optString("description", "");
            JSONObject params = testJson.getJSONObject("parameters");
            int width = params.getInt("width");
            int height = params.getInt("height");
            float density = (float) params.optDouble("density", 1.0);
            int frames = params.optInt("frames", 2);
            double tolerance = params.optDouble("tolerance", 0.5);

            JSONObject docObj = testJson.getJSONObject("document");

            // Compile to binary using RemoteComposeWriter + RemoteComposeJsonParser
            RemoteComposeWriter writer = new RemoteComposeWriter(
                    width, height, name, CoreDocument.DOCUMENT_API_LEVEL,
                    androidx.compose.remote.core.RcProfiles.PROFILE_ANDROIDX
                            | androidx.compose.remote.core.RcProfiles.PROFILE_EXPERIMENTAL,
                    new MockPlatform());
            RemoteComposeJsonParser parser = new RemoteComposeJsonParser(writer);
            try {
                parser.parse(docObj.toString());
            } catch (Exception e) {
                // The Java parser does not understand some construct in this test. Leave any
                // existing gold file untouched and carry on; the gap is reported at the end
                // and surfaced in gold-overview.json.
                String reason = e.getMessage() == null ? e.toString() : e.getMessage();
                reason = reason.replace('\n', ' ').trim();
                unsupported.add(name + " :: " + reason);
                System.out.println("SKIPPED (java parser gap): " + name + " -- " + reason);
                continue;
            }

            byte[] rcBytes = writer.encodeToByteArray();
            assertNotNull(rcBytes);
            assertTrue(rcBytes.length > 0);
            String base64Doc = Base64.getEncoder().encodeToString(rcBytes);

            // Ingest into CoreDocument and compute layout
            RemoteComposeBuffer buffer = RemoteComposeBuffer.fromInputStream(new ByteArrayInputStream(rcBytes));
            HarnessClock clock = new HarnessClock();
            CoreDocument doc = new CoreDocument(clock);
            doc.initFromBuffer(buffer);

            HeadlessRemoteContext remote = new HeadlessRemoteContext();
            clock.mContext = remote;
            // The animation and interaction phases below both start their timeline at
            // BASE_TIME_MILLIS. Run the warm-up frames on the second leading up to it, so an
            // operation that latches "first painted at" (the marquee does) records a time on the
            // same axis as the frames that follow, and time only ever moves forwards.
            remote.currentTime = WARM_UP_START_MILLIS;
            remote.mWidth = width;
            remote.mHeight = height;
            remote.setDensity(density);
            doc.initializeContext(remote);
            // initializeContext assigns context.mDocument directly (CoreDocument.java:1629)
            // instead of going through RemoteContext.setDocument(), which is the only path that
            // copies the document's clock onto the context. Without this the context keeps
            // RemoteClock.SYSTEM and everything reading context.getClock() -- which is what
            // PaintContext.getClock() resolves to -- would still sample the wall clock.
            remote.setClock(clock);
            remote.loadFloat(5, width);
            remote.loadFloat(6, height);
            doc.applyDataOperations(remote);

            for (int f = 0; f < frames; f++) {
                // The clock deliberately does not advance across warm-up frames. They are
                // repeated measurements of a single instant, taken until the layout stops
                // changing, not a passage of time. Any gap between them reads as elapsed time to
                // AnimatableValue (AnimatableValue.java:92-101), which would start a size
                // animation and leave the captured tree mid-flight instead of settled.
                remote.setAnimationTime(f / 60.0f);
                doc.measure(remote, 0, width, 0, height);
                doc.paint(remote, Theme.UNSPECIFIED);
            }
            JSONArray initialTreeArr = extractSettledTree(doc, remote);

            JSONObject goldJson = new JSONObject();
            goldJson.put("name", name);
            goldJson.put("description", description);
            boolean isSuspicious = false;
            String suspReason = "";
            if (testJson.has("tags")) {
                goldJson.put("tags", testJson.getJSONArray("tags"));
                if (testJson.getJSONArray("tags").toString().contains("suspicious")) {
                    isSuspicious = true;
                }
            }
            if (testJson.has("suspicious_reason")) {
                suspReason = testJson.getString("suspicious_reason");
                goldJson.put("suspicious_reason", suspReason);
                isSuspicious = true;
            }
            goldJson.put("parameters", params);
            goldJson.put("document_base64", base64Doc);

            // 1. Check if test has resize_steps
            if (params.has("resize_steps")) {
                remote.setAnimationEnabled(false);
                JSONArray steps = params.getJSONArray("resize_steps");
                JSONArray expectedResizeArr = new JSONArray();
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    int sw = step.getInt("width");
                    int sh = step.getInt("height");
                    remote.mWidth = sw;
                    remote.mHeight = sh;
                    remote.loadFloat(5, sw);
                    remote.loadFloat(6, sh);
                    for (int f = 0; f < frames; f++) {
                        remote.setAnimationTime(f / 60.0f);
                        doc.measure(remote, 0, sw, 0, sh);
                        doc.paint(remote, Theme.UNSPECIFIED);
                    }

                    JSONObject stepObj = new JSONObject();
                    stepObj.put("step", i);
                    stepObj.put("width", sw);
                    stepObj.put("height", sh);
                    if (step.has("label")) stepObj.put("label", step.getString("label"));
                    stepObj.put("tree", extractSettledTree(doc, remote));
                    expectedResizeArr.put(stepObj);
                }
                goldJson.put("expected_resize_steps", expectedResizeArr);
            }

            // 2. Check if test has animation
            if (params.has("animation")) {
                JSONObject anim = params.getJSONObject("animation");
                int totalFrames = anim.optInt("total_frames", 30);
                JSONArray captureFramesArr = anim.getJSONArray("capture_frames");
                Set<Integer> captureSet = new HashSet<>();
                for (int i = 0; i < captureFramesArr.length(); i++) {
                    captureSet.add(captureFramesArr.getInt(i));
                }

                long baseTime = BASE_TIME_MILLIS;
                if (anim.has("trigger")) {
                    remote.currentTime = baseTime;
                    JSONObject trigger = anim.getJSONObject("trigger");
                    String tType = trigger.getString("type");
                    if ("resize".equalsIgnoreCase(tType)) {
                        int tw = trigger.getInt("width");
                        int th = trigger.getInt("height");
                        remote.mWidth = tw;
                        remote.mHeight = th;
                        remote.loadFloat(5, tw);
                        remote.loadFloat(6, th);
                        doc.measure(remote, 0, tw, 0, th);
                    } else if ("click".equalsIgnoreCase(tType)) {
                        float cx = (float) trigger.getDouble("x");
                        float cy = (float) trigger.getDouble("y");
                        doc.onClick(remote, cx, cy);
                        doc.paint(remote, Theme.UNSPECIFIED);
                        doc.measure(remote, 0, width, 0, height);
                    }
                }

                JSONArray expectedFramesArr = new JSONArray();
                for (int f = 0; f <= totalFrames; f++) {
                    float timeSec = f / 60.0f;
                    remote.currentTime = baseTime + Math.round(timeSec * 1000.0f);
                    remote.setAnimationTime(timeSec);
                    remote.loadFloat(RemoteContext.ID_ANIMATION_TIME, timeSec);
                    remote.loadFloat(RemoteContext.ID_ANIMATION_DELTA_TIME, 1f / 60.0f);
                    doc.paint(remote, Theme.UNSPECIFIED);

                    if (captureSet.contains(f)) {
                        JSONObject fObj = new JSONObject();
                        fObj.put("frame", f);
                        fObj.put("time", Math.round(timeSec * 1000.0) / 1000.0);
                        fObj.put("tree", extractTree(doc));
                        expectedFramesArr.put(fObj);
                    }
                }
                goldJson.put("expected_frames", expectedFramesArr);
            }

            // 3. Check if test has interactions
            if (params.has("interactions")) {
                JSONArray interactions = params.getJSONArray("interactions");
                JSONArray expectedInteractionsArr = new JSONArray();
                remote.currentTime = BASE_TIME_MILLIS;
                remote.setAnimationTime(remote.currentTime / 1000f);
                for (int i = 0; i < interactions.length(); i++) {
                    JSONObject act = interactions.getJSONObject(i);
                    // Dispatch is matched case-insensitively for the author's convenience, but
                    // the name recorded on the gold is the one that was *declared*. It becomes
                    // the timeline step's `kind`, and the format spells the compound gestures
                    // `longPress` / `doubleClick`; folding case into the recorded name would
                    // emit `longpress`, which no player recognises as a gesture at all -- the
                    // step would silently degrade into a no-op and the assertions bound to it
                    // would compare the document against itself.
                    String declaredType = act.getString("type");
                    String type = declaredType.toLowerCase(Locale.US);
                    float ax = (float) act.optDouble("x", 0.0);
                    float ay = (float) act.optDouble("y", 0.0);
                    if (act.has("time_millis")) {
                        long tm = act.getLong("time_millis");
                        remote.currentTime += tm;
                        remote.setAnimationTime(remote.currentTime / 1000f);
                    } else if (act.has("delta_millis")) {
                        long dm = act.getLong("delta_millis");
                        remote.currentTime += dm;
                        remote.setAnimationTime(remote.currentTime / 1000f);
                    }
                    switch (type) {
                        case "click":
                            doc.onClick(remote, ax, ay);
                            break;
                        case "longpress":
                            doc.onLongPress(remote, ax, ay);
                            break;
                        case "doubleclick":
                            doc.onDoubleClick(remote, ax, ay);
                            break;
                        case "touch_down":
                            doc.touchDown(remote, ax, ay);
                            break;
                        case "touch_drag":
                            doc.touchDrag(remote, ax, ay);
                            break;
                        case "touch_up":
                            float dx = (float) act.optDouble("dx", 0.0);
                            float dy = (float) act.optDouble("dy", 0.0);
                            doc.touchUp(remote, ax, ay, dx, dy);
                            break;
                        case "advance_time":
                            // Nothing to dispatch: the clock was already moved above. The step
                            // still captures a tree, which is the point -- it observes whatever
                            // the preceding gesture left animating.
                            break;
                        default:
                            // Falling through silently would record a tree unchanged from the
                            // previous step and present it as evidence that the gesture did
                            // nothing, which is indistinguishable from a genuine no-op.
                            throw new IllegalArgumentException(
                                    "unknown interaction type '" + declaredType + "' in " + name);
                    }
                    doc.paint(remote, Theme.UNSPECIFIED);
                    doc.measure(remote, 0, remote.mWidth, 0, remote.mHeight);
                    doc.paint(remote, Theme.UNSPECIFIED);

                    JSONObject intObj = new JSONObject();
                    intObj.put("step", i);
                    intObj.put("action", declaredType);
                    if (act.has("label")) intObj.put("label", act.getString("label"));
                    intObj.put("tree", extractSettledTree(doc, remote));
                    expectedInteractionsArr.put(intObj);
                }
                goldJson.put("expected_interactions", expectedInteractionsArr);
            }

            // The tree measured after the warm-up frames, before the resize sweep, animation
            // phase or gesture sequence runs. attachUnifiedFormat binds this to the `initial`
            // step, so it has to be the tree that step produced -- and only that one.
            //
            // This used to fall back to the tree left behind at the *end* of the run whenever a
            // test had an animation or interaction phase, which labelled a final state as the
            // initial one. Every check bound to `initial` on those golds was then unsatisfiable
            // by any player replaying the timeline faithfully; the later phases already carry
            // their own per-step trees, so nothing is lost by dropping the fallback.
            JSONArray expectedTreeArr = initialTreeArr;
            goldJson.put("expected_tree", expectedTreeArr);

            StringBuilder dump = new StringBuilder();
            for (int i = 0; i < expectedTreeArr.length(); i++) {
                JSONObject c = expectedTreeArr.getJSONObject(i);
                dump.append(String.format(Locale.US, "LAYOUT id=%d x=%.2f y=%.2f w=%.2f h=%.2f\n",
                        c.getInt("id"), c.getDouble("x"), c.getDouble("y"), c.getDouble("width"), c.getDouble("height")));
            }
            goldJson.put("raw_layout_dump", dump.toString().trim());

            File goldFile = new File(goldDir, name + ".gold.json");
            // Derive the portable timeline + checks. These are the gold's only assertion
            // vocabulary; the expected_* keys assembled above are scratch input to the
            // derivation and are stripped again by writeGold.
            attachUnifiedFormat(goldJson, "layout");
            // If the derivation produced no checks the generator did not understand the test
            // at all, and its freshly encoded (effectively empty) document must not replace
            // the good one on disk.
            int ownAssertions = countAssertions(goldJson);
            if (ownAssertions == 0) {
                unsupported.add(name + " :: generator derived 0 assertions of its own");
                System.out.println("SKIPPED (generator understood nothing): " + name);
                continue;
            }
            List<String> carried = preserveForeignAssertions(goldFile, goldJson);
            if (!carried.isEmpty()) {
                System.out.println("  preserved foreign assertions on " + name + ": " + carried);
            }
            writeGold(goldFile, goldJson);

            System.out.println("Generated gold file: " + goldFile.getName() + " (" + expectedTreeArr.length() + " components)");
            processed++;
        }

        System.out.println("Successfully generated " + processed + " conformance gold files!");
        if (!unsupported.isEmpty()) {
            System.out.println("");
            System.out.println("==== " + unsupported.size()
                    + " layout test(s) the Java generator cannot express ====");
            for (String s : unsupported) {
                System.out.println("  - " + s);
            }
            System.out.println("Their existing gold files were left untouched.");
            System.out.println("");
        }

        String envOpt = System.getenv("GENERATE_GOLD_OVERVIEW");
        boolean generateOverview = envOpt != null
                ? Boolean.parseBoolean(envOpt)
                : Boolean.parseBoolean(System.getProperty("generate.gold.overview", "true"));
        if (generateOverview) {
            // Exercise the whole corpus before dumping JaCoCo, otherwise coverage only
            // reflects the layout/expression/particle tests this class encodes itself.
            replayGoldCorpus(conformanceDir);
            SubsystemCoverageReport coverage = computeCoverage(conformanceDir);
            writeCoverageReport(conformanceDir, coverage);
        }
    }

    /**
     * Top-level keys this generator authors itself. Anything in a gold file that is not in this
     * set and not {@linkplain #isLegacyGoldKey legacy} belongs to someone else and is carried
     * over on regeneration.
     */
    private static final Set<String> GENERATOR_OWNED_KEYS = new HashSet<>(Arrays.asList(
            "name", "category", "description", "parameters", "document_base64",
            "format_version", "profile", "harness", "timeline", "checks",
            "tags", "suspicious_reason"));

    /**
     * Pre-v2 assertion keys. These were folded into {@code timeline} + {@code checks} by the
     * one-off migration and must never be resurrected: a stale {@code expected_tree} carried
     * forward onto a freshly measured document would assert the <em>old</em> layout.
     */
    private static final Set<String> LEGACY_GOLD_KEYS = new HashSet<>(Arrays.asList(
            "raw_layout_dump", "gold_image_base64", "final_particles", "snapshots",
            "interactions", "assert_distinct_ids", "instance_args", "tolerance"));

    /** True for a pre-v2 assertion key, which is dropped rather than written or carried. */
    private static boolean isLegacyGoldKey(String key) {
        return key.startsWith("expected_") || LEGACY_GOLD_KEYS.contains(key);
    }

    /**
     * Carries over anything in the existing {@code goldFile} that this generator cannot
     * reproduce, so that regenerating never silently weakens a gold.
     *
     * <p>Two things are rescued:
     *
     * <ol>
     *   <li><b>Unknown top-level keys</b> — {@code known_divergence}, {@code unasserted}, and
     *       any field added by later tooling.
     *   <li><b>Foreign checks</b> — entries in {@code checks} whose {@code probe} this
     *       generator never derives (a {@code raster} comparison, say). Without this merge a
     *       regenerated gold would quietly drop the assertion and the test would keep
     *       reporting PASS while verifying strictly less than before.
     * </ol>
     *
     * <p>A foreign check pinned to a step that no longer exists in the derived timeline is
     * dropped with a loud warning rather than silently, since it cannot be evaluated.
     *
     * @return the list of keys and probes that were carried over, for reporting
     */
    private static List<String> preserveForeignAssertions(File goldFile, JSONObject goldJson) {
        List<String> carried = new ArrayList<>();
        if (!goldFile.exists()) {
            return carried;
        }
        try {
            byte[] b = new byte[(int) goldFile.length()];
            try (FileInputStream fis = new FileInputStream(goldFile)) {
                fis.read(b);
            }
            JSONObject existing = new JSONObject(new String(b, StandardCharsets.UTF_8));

            for (String key : existing.keySet()) {
                if (GENERATOR_OWNED_KEYS.contains(key) || isLegacyGoldKey(key)) continue;
                if (goldJson.has(key)) continue;
                goldJson.put(key, existing.get(key));
                carried.add(key);
            }

            JSONArray mine = goldJson.optJSONArray("checks");
            JSONArray theirs = existing.optJSONArray("checks");
            if (mine != null && theirs != null) {
                Set<String> myProbes = new HashSet<>();
                for (int i = 0; i < mine.length(); i++) {
                    myProbes.add(mine.getJSONObject(i).optString("probe"));
                }
                Set<String> steps = timelineStepIds(goldJson.optJSONArray("timeline"));
                for (int i = 0; i < theirs.length(); i++) {
                    JSONObject check = theirs.getJSONObject(i);
                    String probe = check.optString("probe");
                    if (myProbes.contains(probe)) continue;
                    String at = check.optString("at");
                    if (!steps.contains(at)) {
                        System.out.println("  WARNING: dropping foreign '" + probe + "' check on "
                                + goldJson.optString("name") + " -- step '" + at
                                + "' no longer exists in the derived timeline");
                        continue;
                    }
                    mine.put(check);
                    carried.add("check:" + probe);
                }
            }
        } catch (Exception ignored) {
            // A corrupt or unreadable existing gold is simply replaced.
        }
        return carried;
    }

    /** Number of individual assertions a gold file makes, used to reject degenerate golds. */
    private static int countAssertions(JSONObject goldJson) {
        JSONArray checks = goldJson.optJSONArray("checks");
        return checks == null ? 0 : checks.length();
    }

    /**
     * Writes {@code goldJson} in the v2 schema: legacy assertion keys are dropped and the
     * remaining keys are emitted in a fixed, readable order so regeneration produces no
     * gratuitous diff. {@code document_base64} goes last because it is one enormous line.
     */
    private static void writeGold(File goldFile, JSONObject goldJson) throws IOException {
        for (String key : new ArrayList<>(goldJson.keySet())) {
            if (isLegacyGoldKey(key)) {
                goldJson.remove(key);
            }
        }
        try (FileOutputStream fos = new FileOutputStream(goldFile)) {
            fos.write(orderedJson(goldJson).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Preferred key order, applied at every level of a gold file. Keys not listed here follow,
     * alphabetically. {@code org.json} backs a JSONObject with a HashMap, so without an explicit
     * order the layout of the file is an artefact of string hashing — stable, but arbitrary and
     * unreadable. {@code document_base64} sits last among the top-level keys because it is one
     * enormous line.
     */
    private static final List<String> GOLD_KEY_ORDER = Arrays.asList(
            // Top level.
            "name", "category", "description", "tags", "suspicious_reason",
            "known_divergence", "known_unimplemented",
            "format_version", "profile", "parameters", "harness",
            "timeline", "checks", "unasserted", "document_base64",
            // Timeline steps and checks.
            "id", "kind", "label", "step", "at", "probe", "target", "index",
            "x", "y", "width", "height", "frames", "animation_enabled", "capture",
            "expect", "tolerance", "advisory");

    /** Serialises a gold file with two-space indentation and {@link #GOLD_KEY_ORDER} applied. */
    private static String orderedJson(JSONObject obj) {
        return render(obj, 0);
    }

    /** Renders one JSON value at {@code depth} levels of two-space indentation. */
    private static String render(Object value, int depth) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject) value;
            if (obj.length() == 0) return "{}";
            return joinEntries(orderKeys(obj), obj, depth);
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            if (arr.length() == 0) return "[]";
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < arr.length(); i++) {
                sb.append(indent(depth + 1)).append(render(arr.get(i), depth + 1));
                sb.append(i < arr.length() - 1 ? ",\n" : "\n");
            }
            return sb.append(indent(depth)).append(']').toString();
        }
        return JSONObject.valueToString(value);
    }

    private static String joinEntries(List<String> keys, JSONObject obj, int depth) {
        StringBuilder sb = new StringBuilder("{\n");
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            sb.append(indent(depth + 1)).append(JSONObject.quote(key)).append(": ");
            sb.append(render(obj.get(key), depth + 1));
            sb.append(i < keys.size() - 1 ? ",\n" : "\n");
        }
        return sb.append(indent(depth)).append('}').toString();
    }

    /** {@link #GOLD_KEY_ORDER} first, then everything else alphabetically. */
    private static List<String> orderKeys(JSONObject obj) {
        List<String> ordered = new ArrayList<>();
        for (String key : GOLD_KEY_ORDER) {
            if (obj.has(key)) ordered.add(key);
        }
        List<String> rest = new ArrayList<>(obj.keySet());
        rest.removeAll(ordered);
        Collections.sort(rest);
        ordered.addAll(rest);
        return ordered;
    }

    private static String indent(int depth) {
        StringBuilder sb = new StringBuilder(depth * 2);
        for (int i = 0; i < depth; i++) sb.append("  ");
        return sb.toString();
    }

    private static androidx.compose.remote.core.operations.ParticlesCreate findParticlesCreate(Operation op) {
        if (op instanceof androidx.compose.remote.core.operations.ParticlesCreate) {
            return (androidx.compose.remote.core.operations.ParticlesCreate) op;
        }
        if (op instanceof Container) {
            for (Operation child : ((Container) op).getList()) {
                androidx.compose.remote.core.operations.ParticlesCreate found = findParticlesCreate(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    public void generateExpressionConformanceGoldFiles() throws Exception {
        File currentDir = new File(".").getCanonicalFile();
        File conformanceDir = findConformanceDir(currentDir);
        assertNotNull("Could not find conformance directory", conformanceDir);

        File testsDir = new File(conformanceDir, "tests/expressions");
        File goldDir = new File(conformanceDir, "gold/expressions");
        if (!goldDir.exists()) {
            goldDir.mkdirs();
        }

        File[] testFiles = testsDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(testFiles);
        assertTrue(testFiles.length > 0);

        int processed = 0;
        List<String> unsupported = new ArrayList<>();
        for (File tf : testFiles) {
            String content;
            try (FileInputStream fis = new FileInputStream(tf)) {
                byte[] b = new byte[(int) tf.length()];
                fis.read(b);
                content = new String(b, StandardCharsets.UTF_8);
            }

            JSONObject testJson = new JSONObject(content);
            String name = testJson.getString("name");
            String description = testJson.optString("description", "");
            JSONObject params = testJson.optJSONObject("parameters");
            if (params == null) params = new JSONObject();
            JSONObject docObj = testJson.getJSONObject("document");
            JSONObject header = docObj.optJSONObject("header");
            int width = header != null ? header.optInt("width", 100) : 100;
            int height = header != null ? header.optInt("height", 100) : 100;

            RemoteComposeWriter writer = new RemoteComposeWriter(
                    width, height, name, CoreDocument.DOCUMENT_API_LEVEL,
                    androidx.compose.remote.core.RcProfiles.PROFILE_ANDROIDX
                            | androidx.compose.remote.core.RcProfiles.PROFILE_EXPERIMENTAL,
                    new MockPlatform());
            RemoteComposeJsonParser parser = new RemoteComposeJsonParser(writer);
            try {
                parser.parse(docObj.toString());
            } catch (Exception e) {
                String reason = e.getMessage() == null ? e.toString() : e.getMessage();
                reason = reason.replace('\n', ' ').trim();
                unsupported.add(name + " :: " + reason);
                System.out.println("SKIPPED (java parser gap): " + name + " -- " + reason);
                continue;
            }
            byte[] rcBytes = writer.encodeToByteArray();
            assertNotNull(rcBytes);
            assertTrue(rcBytes.length > 0);
            String base64Doc = Base64.getEncoder().encodeToString(rcBytes);

            RemoteComposeBuffer buffer = RemoteComposeBuffer.fromInputStream(new ByteArrayInputStream(rcBytes));
            CoreDocument doc = new CoreDocument();
            doc.initFromBuffer(buffer);

            HeadlessRemoteContext remote = new HeadlessRemoteContext();
            remote.mWidth = width;
            remote.mHeight = height;
            doc.initializeContext(remote);
            remote.loadFloat(5, width);
            remote.loadFloat(6, height);
            doc.applyDataOperations(remote);

            JSONObject goldJson = new JSONObject();
            goldJson.put("name", name);
            goldJson.put("category", "expressions");
            goldJson.put("description", description);
            goldJson.put("parameters", params);
            goldJson.put("document_base64", base64Doc);

            // Capture the document's initial state *before* any clock manipulation. The
            // animation loop below leaves the clock parked at its last sample, so reading the
            // variables afterwards would label that sample "initial".
            doc.paint(remote, Theme.UNSPECIFIED);
            JSONObject expectedVars = new JSONObject();
            for (Map.Entry<String, Integer> entry : remote.nameToId.entrySet()) {
                float v = remote.getFloat(entry.getValue());
                expectedVars.put(entry.getKey(), Math.round(v * 10000.0) / 10000.0);
            }
            goldJson.put("expected_variables", expectedVars);

            // Integer and colour expressions have no name to look up — they are addressed by
            // the id the test assigns them, so the test document is the only place the
            // generator can learn which ids exist.
            JSONObject expectedIntegers = captureDeclaredIds(docObj, "integerExpressions", remote, false);
            if (expectedIntegers.length() > 0) {
                goldJson.put("expected_integers", expectedIntegers);
            }
            JSONObject expectedColors = captureDeclaredIds(docObj, "colorExpressions", remote, true);
            if (expectedColors.length() > 0) {
                goldJson.put("expected_colors", expectedColors);
            }

            if (params.has("animation_steps")) {
                JSONArray steps = params.getJSONArray("animation_steps");
                JSONArray expectedSteps = new JSONArray();
                for (int s = 0; s < steps.length(); s++) {
                    float t = (float) steps.getDouble(s);
                    remote.setAnimationTime(t);
                    remote.loadFloat(RemoteContext.ID_ANIMATION_TIME, t);
                    remote.loadFloat(RemoteContext.ID_CONTINUOUS_SEC, t);
                    doc.paint(remote, Theme.UNSPECIFIED);

                    JSONObject stepObj = new JSONObject();
                    stepObj.put("time", t);
                    JSONObject stepVars = new JSONObject();
                    for (Map.Entry<String, Integer> entry : remote.nameToId.entrySet()) {
                        float v = remote.getFloat(entry.getValue());
                        stepVars.put(entry.getKey(), Math.round(v * 10000.0) / 10000.0);
                    }
                    stepObj.put("variables", stepVars);
                    expectedSteps.put(stepObj);
                }
                goldJson.put("expected_steps", expectedSteps);
            }

            File goldFile = new File(goldDir, name + ".gold.json");
            // Derive the portable timeline + checks. These are the gold's only assertion
            // vocabulary; the expected_* keys assembled above are scratch input to the
            // derivation and are stripped again by writeGold.
            attachUnifiedFormat(goldJson, "expressions");
            // This generator can only derive checks from named variables. If it derived none,
            // it did not understand the test; keep whatever is already on disk rather than
            // replacing a working gold with an empty document.
            int ownAssertions = countAssertions(goldJson);
            if (ownAssertions == 0) {
                unsupported.add(name + " :: generator derived 0 assertions of its own");
                System.out.println("SKIPPED (generator understood nothing): " + name);
                continue;
            }
            List<String> carried = preserveForeignAssertions(goldFile, goldJson);
            if (!carried.isEmpty()) {
                System.out.println("  preserved foreign assertions on " + name + ": " + carried);
            }
            writeGold(goldFile, goldJson);

            System.out.println("Generated expression gold file: " + goldFile.getName() + " (" + expectedVars.length() + " variables)");
            processed++;
        }
        System.out.println("Successfully generated " + processed + " expression gold files!");
        if (!unsupported.isEmpty()) {
            System.out.println("");
            System.out.println("==== " + unsupported.size()
                    + " expression test(s) the Java generator cannot express ====");
            for (String s : unsupported) {
                System.out.println("  - " + s);
            }
            System.out.println("Their existing gold files were left untouched.");
            System.out.println("");
        }
    }

    @Test
    public void generateParticleConformanceGoldFiles() throws Exception {
        File currentDir = new File(".").getCanonicalFile();
        File conformanceDir = findConformanceDir(currentDir);
        assertNotNull("Could not find conformance directory", conformanceDir);

        File testsDir = new File(conformanceDir, "tests/particles");
        File goldDir = new File(conformanceDir, "gold/particles");
        if (!goldDir.exists()) {
            goldDir.mkdirs();
        }

        File[] testFiles = testsDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(testFiles);
        assertTrue(testFiles.length > 0);

        int processed = 0;
        for (File tf : testFiles) {
            String content;
            try (FileInputStream fis = new FileInputStream(tf)) {
                byte[] b = new byte[(int) tf.length()];
                fis.read(b);
                content = new String(b, StandardCharsets.UTF_8);
            }

            JSONObject testJson = new JSONObject(content);
            String name = testJson.getString("name");
            String description = testJson.optString("description", "");
            JSONObject params = testJson.optJSONObject("parameters");
            if (params == null) params = new JSONObject();
            int frames = params.optInt("frames", 5);
            JSONObject docObj = testJson.getJSONObject("document");
            JSONObject header = docObj.optJSONObject("header");
            int width = header != null ? header.optInt("width", 200) : 200;
            int height = header != null ? header.optInt("height", 200) : 200;

            RemoteComposeWriter writer = new RemoteComposeWriter(
                    width, height, name, CoreDocument.DOCUMENT_API_LEVEL,
                    androidx.compose.remote.core.RcProfiles.PROFILE_ANDROIDX
                            | androidx.compose.remote.core.RcProfiles.PROFILE_EXPERIMENTAL,
                    new MockPlatform());
            RemoteComposeJsonParser parser = new RemoteComposeJsonParser(writer);
            parser.parse(docObj.toString());

            byte[] rcBytes = writer.encodeToByteArray();
            assertNotNull(rcBytes);
            assertTrue(rcBytes.length > 0);
            String base64Doc = Base64.getEncoder().encodeToString(rcBytes);

            RemoteComposeBuffer buffer = RemoteComposeBuffer.fromInputStream(new ByteArrayInputStream(rcBytes));
            CoreDocument doc = new CoreDocument();
            doc.initFromBuffer(buffer);

            HeadlessRemoteContext remote = new HeadlessRemoteContext();
            remote.mWidth = width;
            remote.mHeight = height;
            doc.initializeContext(remote);
            remote.loadFloat(5, width);
            remote.loadFloat(6, height);
            doc.applyDataOperations(remote);

            // Find ParticlesCreate
            androidx.compose.remote.core.operations.ParticlesCreate particlesCreate = null;
            for (Object obj : remote.objectMap.values()) {
                if (obj instanceof androidx.compose.remote.core.operations.ParticlesCreate) {
                    particlesCreate = (androidx.compose.remote.core.operations.ParticlesCreate) obj;
                    break;
                }
            }
            if (particlesCreate == null) {
                for (Operation op : doc.getOperations()) {
                    particlesCreate = findParticlesCreate(op);
                    if (particlesCreate != null) break;
                }
            }
            assertNotNull("ParticlesCreate operation not found in test " + name, particlesCreate);

            JSONArray expectedFrames = new JSONArray();
            for (int f = 0; f <= frames; f++) {
                remote.setAnimationTime(f / 60.0f);
                remote.loadFloat(RemoteContext.ID_ANIMATION_TIME, f / 60.0f);
                doc.paint(remote, Theme.UNSPECIFIED);

                float[][] current = particlesCreate.getParticles();
                JSONObject fObj = new JSONObject();
                fObj.put("frame", f);
                JSONArray pArr = new JSONArray();
                for (int i = 0; i < current.length; i++) {
                    JSONArray row = new JSONArray();
                    for (int j = 0; j < current[i].length; j++) {
                        row.put(Math.round(current[i][j] * 100.0) / 100.0);
                    }
                    pArr.put(row);
                }
                fObj.put("particles", pArr);
                expectedFrames.put(fObj);
            }

            float[][] finalMatrix = particlesCreate.getParticles();
            JSONArray finalArr = new JSONArray();
            for (int i = 0; i < finalMatrix.length; i++) {
                JSONArray row = new JSONArray();
                for (int j = 0; j < finalMatrix[i].length; j++) {
                    row.put(Math.round(finalMatrix[i][j] * 100.0) / 100.0);
                }
                finalArr.put(row);
            }

            JSONObject goldJson = new JSONObject();
            goldJson.put("name", name);
            goldJson.put("category", "particles");
            goldJson.put("description", description);
            goldJson.put("parameters", params);
            goldJson.put("document_base64", base64Doc);
            goldJson.put("expected_frames", expectedFrames);
            goldJson.put("final_particles", finalArr);

            File goldFile = new File(goldDir, name + ".gold.json");
            // Derive the portable timeline + checks. These are the gold's only assertion
            // vocabulary; the expected_* keys assembled above are scratch input to the
            // derivation and are stripped again by writeGold.
            attachUnifiedFormat(goldJson, "particles");
            List<String> carried = preserveForeignAssertions(goldFile, goldJson);
            if (!carried.isEmpty()) {
                System.out.println("  preserved foreign assertions on " + name + ": " + carried);
            }
            writeGold(goldFile, goldJson);

            System.out.println("Generated particle gold file: " + goldFile.getName() + " (" + finalMatrix.length + " particles)");
            processed++;
        }
        System.out.println("Successfully generated " + processed + " particle gold files!");
    }

    /**
     * Categories whose gold files are hand-authored: their {@code checks} encode knowledge the
     * generators above cannot derive (glyph run geometry, recorded draw commands, decoded op
     * names), so nothing here re-derives them. What was missing was any way to rebuild the one
     * part of those files that *is* mechanically derivable — {@code document_base64}. Without it
     * the test JSON beside the gold was decoration: editing it changed nothing a player ever saw,
     * because the binary is what ships.
     *
     * <p>{@link #regenerateGoldDocumentBinaries()} closes that gap for exactly these categories.
     */
    private static final List<String> DOCUMENT_ONLY_CATEGORIES = Arrays.asList(
            "animationspec", "canvas", "clock", "colortheme", "conditionals", "dataoperations",
            "interactivity", "loom", "matrixmath", "pathoperations", "scheduling", "semantics",
            "shaders", "textoperations", "wire");

    /**
     * Recompiles {@code document_base64} from the test JSON for every gold in
     * {@link #DOCUMENT_ONLY_CATEGORIES}, leaving every other key — including all hand-authored
     * assertions — exactly as it found it.
     *
     * <p>Read-only unless {@code REGENERATE_GOLD_DOCUMENTS=true}, so an ordinary test run reports
     * drift between a test JSON and the binary it is supposed to describe without rewriting
     * anything. {@code GOLD_DOCUMENT_FILTER=<substring>} narrows the sweep.
     *
     * <p>A recompiled binary that no longer satisfies the gold's hand-authored checks is a real
     * finding, not a regression to paper over: either the test JSON never described the document
     * that was shipped, or the checks were written against a document nobody can rebuild.
     */
    @Test
    public void regenerateGoldDocumentBinaries() throws Exception {
        File currentDir = new File(".").getCanonicalFile();
        File conformanceDir = findConformanceDir(currentDir);
        assertNotNull("Could not find conformance directory", conformanceDir);

        boolean write = "true".equals(System.getenv("REGENERATE_GOLD_DOCUMENTS"));
        String filter = System.getenv("GOLD_DOCUMENT_FILTER");

        List<String> changed = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        List<String> missingGold = new ArrayList<>();
        int examined = 0;

        for (String category : DOCUMENT_ONLY_CATEGORIES) {
            File testsDir = new File(conformanceDir, "tests/" + category);
            File goldDir = new File(conformanceDir, "gold/" + category);
            File[] testFiles = testsDir.listFiles((dir, n) -> n.endsWith(".json"));
            if (testFiles == null) continue;
            Arrays.sort(testFiles);

            for (File tf : testFiles) {
                String content;
                try (FileInputStream fis = new FileInputStream(tf)) {
                    byte[] b = new byte[(int) tf.length()];
                    fis.read(b);
                    content = new String(b, StandardCharsets.UTF_8);
                }
                JSONObject testJson = new JSONObject(content);
                String name = testJson.getString("name");
                if (filter != null && !filter.isEmpty() && !name.contains(filter)) continue;

                File goldFile = new File(goldDir, name + ".gold.json");
                if (!goldFile.exists()) {
                    // No gold to update. Authoring one would mean inventing assertions, which is
                    // the one thing this pass deliberately does not do.
                    missingGold.add(category + "/" + name);
                    continue;
                }
                examined++;

                JSONObject docObj = testJson.optJSONObject("document");
                if (docObj == null) {
                    unsupported.add(category + "/" + name + " :: no \"document\" block");
                    continue;
                }
                JSONObject header = docObj.optJSONObject("header");
                JSONObject params = testJson.optJSONObject("parameters");
                int width = header != null && header.has("width") ? header.getInt("width")
                        : params != null ? params.optInt("width", 200) : 200;
                int height = header != null && header.has("height") ? header.getInt("height")
                        : params != null ? params.optInt("height", 200) : 200;

                String base64Doc;
                try {
                    RemoteComposeWriter writer = new RemoteComposeWriter(
                            width, height, name, CoreDocument.DOCUMENT_API_LEVEL,
                            androidx.compose.remote.core.RcProfiles.PROFILE_ANDROIDX
                                    | androidx.compose.remote.core.RcProfiles.PROFILE_EXPERIMENTAL,
                            new MockPlatform());
                    new RemoteComposeJsonParser(writer).parse(docObj.toString());
                    byte[] rcBytes = writer.encodeToByteArray();
                    assertNotNull(rcBytes);
                    assertTrue(rcBytes.length > 0);
                    base64Doc = Base64.getEncoder().encodeToString(rcBytes);
                } catch (Exception e) {
                    String reason = e.getMessage() == null ? e.toString() : e.getMessage();
                    unsupported.add(category + "/" + name + " :: " + reason.replace('\n', ' ').trim());
                    continue;
                }

                String goldContent;
                try (FileInputStream fis = new FileInputStream(goldFile)) {
                    byte[] b = new byte[(int) goldFile.length()];
                    fis.read(b);
                    goldContent = new String(b, StandardCharsets.UTF_8);
                }
                JSONObject goldJson = new JSONObject(goldContent);
                if (base64Doc.equals(goldJson.optString("document_base64"))) continue;

                changed.add(category + "/" + name);
                if (write) {
                    goldJson.put("document_base64", base64Doc);
                    writeGold(goldFile, goldJson);
                    System.out.println("Rebuilt document binary: " + category + "/" + name);
                }
            }
        }

        System.out.println("Examined " + examined + " hand-authored golds across "
                + DOCUMENT_ONLY_CATEGORIES.size() + " categories.");
        if (!missingGold.isEmpty()) {
            System.out.println("  no gold file (" + missingGold.size() + "): " + missingGold);
        }
        if (!unsupported.isEmpty()) {
            System.out.println("  java parser cannot express (" + unsupported.size() + "):");
            for (String u : unsupported) System.out.println("    " + u);
        }
        if (changed.isEmpty()) {
            System.out.println("  all document binaries already match their test JSON.");
        } else if (write) {
            System.out.println("  rewrote " + changed.size() + " document binaries.");
        } else {
            System.out.println("  " + changed.size() + " binaries DRIFT from their test JSON "
                    + "(set REGENERATE_GOLD_DOCUMENTS=true to rebuild): " + changed);
        }
    }
}
