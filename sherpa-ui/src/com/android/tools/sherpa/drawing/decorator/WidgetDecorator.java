/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.sherpa.drawing.decorator;

import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.ConnectionDraw;
import com.android.tools.sherpa.drawing.SceneDraw;
import com.android.tools.sherpa.drawing.SnapDraw;
import com.android.tools.sherpa.drawing.ViewTransform;
import com.android.tools.sherpa.interaction.ConstraintHandle;
import com.android.tools.sherpa.interaction.DrawPicker;
import com.android.tools.sherpa.interaction.WidgetInteractionTargets;
import com.android.tools.sherpa.structure.Selection;
import com.android.tools.sherpa.drawing.WidgetDraw;
import com.android.tools.sherpa.structure.WidgetCompanion;
import android.support.constraint.solver.widgets.ConstraintAnchor;
import android.support.constraint.solver.widgets.ConstraintWidget;
import android.support.constraint.solver.widgets.ConstraintWidgetContainer;

import javax.imageio.ImageIO;
import javax.swing.Timer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;

/**
 * Base class for painting a widget in blueprint mode
 */
public class WidgetDecorator {

    public static final int BLUEPRINT_STYLE = 0;
    public static final int ANDROID_STYLE = 1;

    public static Image sLockImageIcon = null;
    public static Image sUnlockImageIcon = null;
    public static Image sDeleteConnectionsImageIcon = null;
    public static Image sPackChainImageIcon = null;

    private static boolean sShowAllConstraints = false;
    private static boolean sShowTextUI = false;

    private static final int ACTIONS_HIDE_TIMEOUT = 1000; // ms

    private boolean mIsVisible = true;
    private boolean mIsSelected = false;
    private boolean mShowResizeHandles = false;
    private boolean mShowSizeIndicator = false;
    private boolean mShowActions = false;
    protected ColorSet mColorSet;

    private SceneDraw.Repaintable mRepaintableSurface;

    private AnimationProgress mShowBaseline = new AnimationProgress();
    private AnimationProgress mShowBias = new AnimationProgress();

    private static Font sInfoFont = new Font("Helvetica", Font.PLAIN, 12);

    private ArrayList<WidgetAction> mWidgetActions = new ArrayList<>();

    private EnumSet<WidgetDraw.ANCHORS_DISPLAY> mDisplayAnchorsPolicy =
            EnumSet.of(WidgetDraw.ANCHORS_DISPLAY.NONE);

    private StateModel mStateModel;

    ColorTheme mBackgroundColor;
    ColorTheme mFrameColor;
    ColorTheme mTextColor;
    ColorTheme mConstraintsColor;

    ColorTheme.Look mLook;

    protected final ConstraintWidget mWidget;
    private int mStyle;

    private final static int ACTION_SIZE = 22;
    private boolean mOver = false;

    public interface StateModel {
        void save(WidgetDecorator decorator);
    }

    private final Timer mHideActions = new Timer(ACTIONS_HIDE_TIMEOUT, e -> {
        mShowActions = false;
        repaint();
    });

    /**
     * Utility function to return a ConstraintHandle instance associated with the anchor
     *
     * @param anchor the given anchor
     * @return returns the associated ConstraintHandle
     */
    public static ConstraintHandle getConstraintHandle(ConstraintAnchor anchor) {
        ConstraintWidget widget = anchor.getOwner();
        WidgetCompanion companion = (WidgetCompanion) widget.getCompanionWidget();
        WidgetInteractionTargets interactionTargets = companion.getWidgetInteractionTargets();
        return interactionTargets.getConstraintHandle(anchor);
    }

    /**
     * Utility class encapsulating a simple animation timer
     */
    class AnimationProgress {
        long mStart = 0;
        long mDelay = 1000;
        long mDuration = 300;

        public void setDelay(long delay) {
            mDelay = delay;
        }

        public void setDuration(long duration) {
            mDuration = duration;
        }

        public void start() {
            mStart = System.currentTimeMillis() + mDelay;
        }

        public float getProgress() {
            if (mStart == 0) {
                return 0;
            }
            long current = System.currentTimeMillis();
            long delta = current - mStart;
            if (delta < 0) {
                return 0;
            }
            if (delta > mDuration) {
                return 1;
            }
            return (current - mStart) / (float) mDuration;
        }

        public boolean isDone() {
            if (mStart == 0) {
                return false;
            }
            long current = System.currentTimeMillis();
            long delta = current - mStart;
            if (delta > mDuration) {
                return true;
            }
            return false;
        }

        public void reset() {
            mStart = 0;
        }

        public boolean isRunning() {
            return mStart != 0 && !isDone();
        }
    }

    /**
     * Utility function to load an image from the resources
     *
     * @param path path of the image
     * @return the image loaded, or null if we couldn't
     */
    public static BufferedImage loadImage(String path) {
        if (path == null) {
            return null;
        }
        try {
            InputStream stream = WidgetDecorator.class.getResourceAsStream(path);
            if (stream != null) {
                BufferedImage image = ImageIO.read(stream);
                return image;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Base constructor
     *
     * @param widget the widget we are decorating
     */
    public WidgetDecorator(ConstraintWidget widget) {
        mWidget = widget;
        mShowBias.setDelay(0);
        mShowBias.setDuration(1000);
        mShowBaseline.setDelay(1000);
        mShowBaseline.setDuration(1000);
        mWidgetActions.add(new DeleteConnectionsWidgetAction(mWidget));
        mWidgetActions.add(new ToggleChainStyleWidgetAction(mWidget));
        mHideActions.setRepeats(false);
    }

    /**
     * Set the state model
     */
    public void setStateModel(StateModel model) { mStateModel = model; }

    /**
     * Getter for the state model object
     * */
    public StateModel getStateModel() { return mStateModel; }

    /**
     * Accessor for the actions on this widget
     *
     * @return list of available actions
     */
    public ArrayList<WidgetAction> getWidgetActions() {
        return mWidgetActions;
    }

    /**
     * Set a repaintable object
     *
     * @param repaintableSurface
     */
    public void setRepaintableSurface(SceneDraw.Repaintable repaintableSurface) {
        mRepaintableSurface = repaintableSurface;
    }

    /**
     * Call repaint() on the repaintable object
     */
    public void repaint() {
        if (mRepaintableSurface != null) {
            mRepaintableSurface.repaint();
        }
    }

    /**
     * Call repaint() on the repaintable object
     */
    public void repaint(int x, int y, int w, int h) {
        if (mRepaintableSurface != null) {
            mRepaintableSurface.repaint(x, y, w, h);
        }
    }

    /**
     * Return true if the baseline will be shown
     *
     * @return
     */
    public boolean isShowBaseline() {
        return mShowBaseline.isDone();
    }

    /**
     * Tell us that the horizontal or vertical bias value has been changed.
     * We'll use it to start showing the bias labels.
     */
    public void updateBias() {
        mShowBias.start();
    }

    /**
     * Set the current color set, and create the local color themes from it
     *
     * @param colorSet the new color set
     */
    public void setColorSet(ColorSet colorSet) {
        if (mColorSet == colorSet) {
            return;
        }
        mColorSet = colorSet;
        if (mColorSet == null) {
            return;
        }
        // Setup the colors we use

        // ColorTheme:
        // subdued
        // normal
        // highlighted
        // selected
        mBackgroundColor = new ColorTheme(
                mColorSet.getSubduedBackground(),
                mColorSet.getBackground(),
                mColorSet.getHighlightedBackground(),
                mColorSet.getSelectedBackground());

        mFrameColor = new ColorTheme(
                mColorSet.getSubduedFrames(),
                mColorSet.getFrames(),
                mColorSet.getHighlightedFrames(),
                mColorSet.getSelectedFrames());

        mTextColor = new ColorTheme(
                mColorSet.getSubduedText(),
                mColorSet.getText(),
                mColorSet.getText(),
                mColorSet.getSelectedText());

        mConstraintsColor = new ColorTheme(
                mColorSet.getSubduedConstraints(),
                mColorSet.getConstraints(),
                mColorSet.getHighlightedConstraints(),
                mColorSet.getSelectedConstraints());
    }

    /**
     * Setter for the visibility of the widget
     *
     * @param isVisible if true, display the widget
     */
    public void setIsVisible(boolean isVisible) {
        mIsVisible = isVisible;
    }

    /**
     * Getter for the visibility of the widget
     *
     * @return the current visibility status, true if visible
     */
    public boolean isVisible() {
        return mIsVisible;
    }

    /**
     * Returns true if the widget is animating (i.e. another repaint will be needed)
     *
     * @return true if animating
     */
    public boolean isAnimating() {
        if (mColorSet == null) {
            return false;
        }
        if (mBackgroundColor.isAnimating()) {
            return true;
        }
        if (mFrameColor.isAnimating()) {
            return true;
        }
        if (mTextColor.isAnimating()) {
            return true;
        }
        if (mConstraintsColor.isAnimating()) {
            return true;
        }
        if (mShowBaseline.isRunning()) {
            return true;
        }
        if (mShowBias.isRunning()) {
            return true;
        }
        return false;
    }

    /**
     * Set showing all constraints for all widgets
     *
     * @param value
     */
    public static void setShowAllConstraints(boolean value) {
        sShowAllConstraints = value;
    }

    /**
     * Set show text UI
     *
     * @param value
     */
    public static void setShowFakeUI(boolean value) {
        sShowTextUI = value;
    }

    /**
     * Accessor returning true if we should show all constraints for all widgets
     *
     * @return true if show all constraints
     */
    public static boolean isShowAllConstraints() {
        return sShowAllConstraints;
    }

    /**
     * Accessor returning true if we want to show the text ui for the widgets
     *
     * @return true if show text ui
     */
    public static boolean isShowFakeUI() {
        return sShowTextUI;
    }

    /**
     * Set the isSelected flag for this decorator
     *
     * @param value
     */
    public void setIsSelected(boolean value) {
        if (mIsSelected == value && mLook != null) {
            return;
        }
        mIsSelected = value;
        if (mIsSelected) {
            setLook(ColorTheme.Look.SELECTED);
            mShowBaseline.start();
            mShowBias.start();
            showActions();
        } else {
            setLook(ColorTheme.Look.NORMAL);
            mShowBaseline.reset();
            mShowBias.reset();
        }
    }

    /**
     * Accessor for the isSelected flag
     *
     * @return true if the decorator/widget is currently selected, false otherwise
     */
    public boolean isSelected() {
        return mIsSelected;
    }

    /**
     * Set to true to show the resize handle for this decorator widget
     *
     * @param value
     */
    public void setShowResizeHandles(boolean value) {
        mShowResizeHandles = value;
    }

    /**
     * Set to true to show widget actions if present
     *
     * @param value
     */
    public void setShowActions(boolean value) {
        mShowActions = value;
    }

    /**
     * Set to true to show the size indicator for this decorator widget
     *
     * @param value
     */
    public void setShowSizeIndicator(boolean value) {
        mShowSizeIndicator = value;
    }

    /**
     * Set to true to show the percent indicator if any
     *
     * @param value
     */
    public void setShowPercentIndicator(boolean value) {
        if (value) {
            mShowBias.start();
        }
    }

    /**
     * Set the current look for this decorator
     *
     * @param look the look to use (normal, subdued, selected, etc.)
     */
    public void setLook(ColorTheme.Look look) {
        mLook = look;
    }

    /**
     * Accessor returning the current look
     *
     * @return the current look for this decorator
     */
    public ColorTheme.Look getLook() {
        return mLook;
    }

    /**
     * Apply the current look if needed
     */
    public void applyLook() {
        if (mColorSet == null) {
            return;
        }
        if (mBackgroundColor.getLook() != mLook) {
            mBackgroundColor.setLook(mLook);
            mFrameColor.setLook(mLook);
            mTextColor.setLook(mLook);
            mConstraintsColor.setLook(mLook);
        }
    }

    /**
     * Called when the mouse is over the widget
     *
     * @param value true if the mouse is over the widget, false otherwise
     */
    public void over(boolean value) {
        mOver = value;
        if (mOver) {
            mShowActions = true;
        }
        mHideActions.restart();
    }

    /**
     * Return the current background color
     *
     * @return current background color
     */
    public Color getBackgroundColor() {
        return mBackgroundColor.getColor();
    }

    /**
     * Main painting function
     *
     * @param transform the view transform
     * @param g         the graphics context
     * @return true if we need to be called again (i.e. if we are animating)
     */
    public boolean onPaint(ViewTransform transform, Graphics2D g) {
        if (mColorSet == null) {
            return false;
        }
        if (mIsSelected) {
            updateShowAnchorsPolicy();
        } else {
            mShowResizeHandles = false;
            mShowSizeIndicator = false;
        }

        if (mColorSet.drawWidgetInfos()) {
            if (mWidget.getVisibility() == ConstraintWidget.INVISIBLE) {
                Color c = mTextColor.getColor();
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 100));
            } else {
                g.setColor(mTextColor.getColor());
            }
            WidgetDraw.drawWidgetInfo(transform, g, mWidget);
        } else {
            onPaintBackground(transform, g);
        }

        g.setColor(mFrameColor.getColor());
        WidgetDraw.drawWidgetFrame(transform, g, mWidget,
                mDisplayAnchorsPolicy, mShowResizeHandles,
                mShowSizeIndicator, mIsSelected);

        g.setColor(mConstraintsColor.getColor());
        onPaintAnchors(transform, g);

        return isAnimating();
    }

    /**
     * Paint the background of the widget
     *
     * @param transform the view transform
     * @param g         the graphics context
     */
    public void onPaintBackground(ViewTransform transform, Graphics2D g) {
        if (mColorSet == null) {
            return;
        }
        if (!mColorSet.drawBackground()) {
            return;
        }
        if (mWidget.isRoot() || mWidget.isRootContainer()) {
            return;
        }
        if (!(mWidget instanceof ConstraintWidgetContainer)
                && mWidget.getVisibility() == ConstraintWidget.VISIBLE) {
            int l = transform.getSwingX(mWidget.getDrawX());
            int t = transform.getSwingY(mWidget.getDrawY());
            int w = transform.getSwingDimension(mWidget.getDrawWidth());
            int h = transform.getSwingDimension(mWidget.getDrawHeight());
            g.setColor(mBackgroundColor.getColor());
            if (mBackgroundColor.getLook() != ColorTheme.Look.NORMAL) {
                g.fillRect(l, t, w, h);
            }

            Color bg = new Color(0, 0, 0, 0);
            Color fg = ColorTheme.updateBrightness(mBackgroundColor.getColor(), 1.6f);
            Graphics2D gfill = (Graphics2D) g.create();
            gfill.setPaint(new LinearGradientPaint(l, t, (l + 2), (t + 2),
                    new float[] { 0, .1f, .1001f }, new Color[] { fg, fg, bg },
                    MultipleGradientPaint.CycleMethod.REFLECT)
            );
            gfill.fillRect(l, t, w, h);
            gfill.dispose();
        }
    }

    /**
     * Paint the anchors of this object
     *
     * @param transform the view transform
     * @param g         the graphics context
     */
    public void onPaintAnchors(ViewTransform transform, Graphics2D g) {
        if (mColorSet == null) {
            return;
        }
        if (mWidget.getVisibility() == ConstraintWidget.GONE) {
            return;
        }

        ConstraintAnchor leftAnchor = mWidget.getAnchor(ConstraintAnchor.Type.LEFT);
        ConstraintAnchor rightAnchor = mWidget.getAnchor(ConstraintAnchor.Type.RIGHT);
        ConstraintAnchor topAnchor = mWidget.getAnchor(ConstraintAnchor.Type.TOP);
        ConstraintAnchor bottomAnchor = mWidget.getAnchor(ConstraintAnchor.Type.BOTTOM);

        boolean leftAnchorIsConnected = leftAnchor != null ? leftAnchor.isConnected() : false;
        boolean rightAnchorIsConnected = rightAnchor != null ? rightAnchor.isConnected() : false;
        boolean topAnchorIsConnected = topAnchor != null ? topAnchor.isConnected() : false;
        boolean bottomAnchorIsConnected = bottomAnchor != null ? bottomAnchor.isConnected() : false;

        boolean displayAllAnchors = mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.ALL);
        boolean showLeftAnchor = displayAllAnchors
                || mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.LEFT)
                || mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.HORIZONTAL);
        boolean showRightAnchor = displayAllAnchors
                || mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.RIGHT)
                || mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.HORIZONTAL);
        boolean showTopAnchor = displayAllAnchors
                || mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.TOP)
                || mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.VERTICAL);
        boolean showBottomAnchor = displayAllAnchors
                || mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.BOTTOM)
                || mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.VERTICAL);

        if (!mDisplayAnchorsPolicy.contains(WidgetDraw.ANCHORS_DISPLAY.NONE)) {
            showLeftAnchor |= leftAnchorIsConnected;
            showRightAnchor |= rightAnchorIsConnected;
            showTopAnchor |= topAnchorIsConnected;
            showBottomAnchor |= bottomAnchorIsConnected;
        }

        WidgetCompanion widgetCompanion = (WidgetCompanion) mWidget.getCompanionWidget();
        WidgetInteractionTargets interactionTargets = widgetCompanion.getWidgetInteractionTargets();

        // Let's draw all the anchors

        g.setColor(mConstraintsColor.getColor());

        // Draw the baseline first, if needed
        if (mWidget.hasBaseline()) {
            ConstraintAnchor baseline = mWidget.getAnchor(ConstraintAnchor.Type.BASELINE);
            if (baseline.isConnected() || (mIsSelected && mShowResizeHandles)) {
                Color c = g.getColor();
                float progress = 1;
                if (!baseline.isConnected()) {
                    progress = mShowBaseline.getProgress();
                    if (progress > 0) {
                        int alpha = (int) (255 * progress);
                        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
                    }
                }
                if (progress > 0) {
                    ConstraintHandle handle = interactionTargets.getConstraintHandle(baseline);
                    handle.draw(transform, g, mColorSet, mIsSelected);
                }
                g.setColor(c);
            }
        }

        if (mIsSelected) {
            g.setColor(mColorSet.getSelectedConstraints());
        }
        if (showLeftAnchor) {
            ConstraintHandle handle = interactionTargets.getConstraintHandle(leftAnchor);
            if (handle != null) {
                handle.draw(transform, g, mColorSet, mIsSelected);
            }
        }
        if (showRightAnchor) {
            ConstraintHandle handle = interactionTargets.getConstraintHandle(rightAnchor);
            if (handle != null) {
                handle.draw(transform, g, mColorSet, mIsSelected);
            }
        }
        if (showTopAnchor) {
            ConstraintHandle handle = interactionTargets.getConstraintHandle(topAnchor);
            if (handle != null) {
                handle.draw(transform, g, mColorSet, mIsSelected);
            }
        }
        if (showBottomAnchor) {
            ConstraintHandle handle = interactionTargets.getConstraintHandle(bottomAnchor);
            if (handle != null) {
                handle.draw(transform, g, mColorSet, mIsSelected);
            }
        }
    }

    /**
     * Paint the constraints of this widget
     *
     * @param transform the view transform
     * @param g         the graphics context
     */
    public void onPaintConstraints(ViewTransform transform, Graphics2D g) {
        if (mColorSet == null) {
            return;
        }
        if (mWidget.getVisibility() == ConstraintWidget.GONE) {
            return;
        }
        g.setColor(mConstraintsColor.getColor());
        if (mIsSelected || isShowAllConstraints() || getLook() == ColorTheme.Look.HIGHLIGHTED) {
            if (mWidget.getVisibility() == ConstraintWidget.INVISIBLE) {
                g.setStroke(SnapDraw.sDashedStroke);
            }
            ArrayList<ConstraintAnchor.Type> anchors = new ArrayList<>();
            if (mWidget.hasBaseline()
                    && (mShowBaseline.isDone()
                        || mWidget.getAnchor(ConstraintAnchor.Type.BASELINE).isConnected())) {
                anchors.add(ConstraintAnchor.Type.BASELINE);
            }
            anchors.add(ConstraintAnchor.Type.LEFT);
            anchors.add(ConstraintAnchor.Type.TOP);
            anchors.add(ConstraintAnchor.Type.RIGHT);
            anchors.add(ConstraintAnchor.Type.BOTTOM);
            Color currentColor = g.getColor();
            Stroke currentStroke = g.getStroke();
            for (ConstraintAnchor.Type type : anchors) {
                ConstraintAnchor anchor = mWidget.getAnchor(type);
                if (anchor == null) {
                    continue;
                }
                if (anchor.isConnected()) {
                    ConstraintAnchor target = anchor.getTarget();
                    if (target.getOwner().getVisibility() == ConstraintWidget.GONE) {
                        continue;
                    }
                    ConstraintHandle startHandle =
                            WidgetInteractionTargets.constraintHandle(anchor);
                    if (startHandle.getAnchor().isConnected()
                            && startHandle.getAnchor().getConnectionCreator()
                            == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                        g.setColor(mColorSet.getSoftConstraintColor());
                        g.setStroke(mColorSet.getSoftConstraintStroke());
                    } else {
                        g.setColor(currentColor);
                        g.setStroke(currentStroke);
                    }
                    boolean painted = false;
                    if (startHandle.isLocking()) {
                        float progress = startHandle.getLockingProgress();
                        if (progress > 0) {
                            startHandle
                                    .drawConnection(transform, g, mColorSet, mIsSelected, true,
                                            startHandle.getAnchor().getConnectionCreator(), progress);
                            painted = true;
                            repaint();
                        }
                    }
                    if (!painted) {
                        startHandle.drawConnection(transform, g, mColorSet, mIsSelected);
                    }
                }
            }
            g.setStroke(SnapDraw.sNormalStroke);
            paintBias(transform, g);
        }
    }

    /**
     * Paint the actions (if any) of this widget
     *
     * @param transform the view transform
     * @param g         the graphics context
     */
    public void onPaintActions(ViewTransform transform, Graphics2D g) {
        if (!mIsSelected) {
            return;
        }
        if (mColorSet == null) {
            return;
        }
        if (mWidget.getVisibility() == ConstraintWidget.GONE) {
            return;
        }
        if (!mShowResizeHandles) {
            return;
        }
        if (!mShowActions) {
            return;
        }
        if (mWidgetActions.size() == 0) {
            return;
        }

        int l = transform.getSwingFX(mWidget.getDrawX());
        int t = transform.getSwingFY(mWidget.getDrawY());
        int h = transform.getSwingDimension(mWidget.getHeight());

        int x = l;
        int y = t + h + ConnectionDraw.CONNECTION_ANCHOR_SIZE + 4;

        g.setColor(mColorSet.getSelectedFrames());
        for (WidgetAction action : mWidgetActions) {
            action.update();
            if (!action.isVisible()) {
                continue;
            }
            action.onPaint(transform, g, x, y);
            x += ACTION_SIZE + ConnectionDraw.CONNECTION_ANCHOR_SIZE;
        }
    }

    static ConstraintHandle getTargetHandle(ConstraintAnchor anchor) {
        return getConstraintHandle(anchor.getTarget());
    }

    /**
     * Paint the horizontal and vertical informations of this widget, if appropriate
     *
     * @param transform the view transform
     * @param g         the graphics context
     */
    private void paintBias(ViewTransform transform, Graphics2D g) {
        ConstraintAnchor left = mWidget.getAnchor(ConstraintAnchor.Type.LEFT);
        ConstraintAnchor right = mWidget.getAnchor(ConstraintAnchor.Type.RIGHT);
        if (left != null && right != null
                && left.isConnected() && right.isConnected()
                && left.getConnectionCreator() != ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
            if (mShowBias.isRunning()) {
                float progress = 1 - mShowBias.getProgress();
                int percent = (int) (mWidget.getHorizontalBiasPercent() * 100);
                int x = mWidget.getDrawX();
                int y = (int) (mWidget.getDrawY() + mWidget.getDrawHeight() / 2f);
                Color c = mColorSet.getTooltipBackground();
                Color pre = g.getColor();
                int alpha = (int)(progress * 255);
                int tx, ty;

                // Draw left
                x = transform.getSwingFX(x);
                y = transform.getSwingFY(y);

                tx = transform.getSwingFX(getTargetHandle(left).getDrawX());
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
                Color textColor =
                  new Color(mColorSet.getTooltipText().getRed(),
                            mColorSet.getTooltipText().getGreen(),
                            mColorSet.getTooltipText().getBlue(),
                            alpha);

                ConnectionDraw.drawRoundRectText(g, sInfoFont, textColor, formatPercent(percent, true), (x + tx) / 2, y);

                // Draw right
                tx = transform.getSwingFX(getTargetHandle(right).getDrawX());
                x = mWidget.getDrawRight();
                x = transform.getSwingFX(x);

                ConnectionDraw.drawRoundRectText(g, sInfoFont, textColor, formatPercent(percent, false), (x + tx) / 2, y);
                g.setColor(pre);
            }
        }
        ConstraintAnchor top = mWidget.getAnchor(ConstraintAnchor.Type.TOP);
        ConstraintAnchor bottom = mWidget.getAnchor(ConstraintAnchor.Type.BOTTOM);
        if (top != null && bottom != null
            && top.isConnected() && bottom.isConnected()
            && mShowBias.isRunning()) {
            float progress = 1 - mShowBias.getProgress();
            int percent = (int)(mWidget.getVerticalBiasPercent() * 100);
            int y = mWidget.getDrawY();
            int x = (int)(mWidget.getDrawX() + mWidget.getDrawWidth() / 2f);
            Color c = mColorSet.getTooltipBackground();
            Color pre = g.getColor();
            int alpha = (int)(progress * 255);
            int tx, ty;

            // draw top
            ty = transform.getSwingFY(getTargetHandle(top).getDrawY());
            x = transform.getSwingFX(x);
            y = transform.getSwingFY(y);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            Color textColor =
              new Color(mColorSet.getTooltipText().getRed(),
                        mColorSet.getTooltipText().getGreen(),
                        mColorSet.getTooltipText().getBlue(),
                        alpha);
            ConnectionDraw.drawRoundRectText(g, sInfoFont, textColor, formatPercent(percent, true), x, (y + ty) / 2);

            // draw bottom
            ty = transform.getSwingFY(getTargetHandle(bottom).getDrawY());
            y = mWidget.getDrawBottom();
            y = transform.getSwingFY(y);
            ConnectionDraw.drawRoundRectText(g, sInfoFont, textColor, formatPercent(percent, false), x, (y + ty) / 2);
            g.setColor(pre);
        }
    }

    /**
     * Utility function to format the percent message
     *
     * @param percent the percent value (0-100)
     * @param begin   if this message will be used in left or top side
     * @return the formatted string representing the percentage
     */
    private String formatPercent(int percent, boolean begin) {
        String message = "" + percent + "%";
        if (begin) {
            if (percent == 25) {
                message = "1/4";
            } else if (percent == 33) {
                message = "1/3";
            } else if (percent == 66) {
                message = "2/3";
            } else if (percent == 75) {
                message = "3/4";
            }
        } else {
            message = "" + (100 - percent) + "%";
            if (percent == 25) {
                message = "3/4";
            } else if (percent == 33) {
                message = "2/3";
            } else if (percent == 66) {
                message = "1/3";
            } else if (percent == 75) {
                message = "1/4";
            }
        }
        return message;
    }

    /**
     * Update the show anchors policy. Used for selected widgets.
     */
    private void updateShowAnchorsPolicy() {
        mDisplayAnchorsPolicy.clear();
        mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.ALL);
        mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.SELECTED);
        if (mWidget.getParent() instanceof ConstraintWidgetContainer) {
            ConstraintWidgetContainer container =
                    (ConstraintWidgetContainer) mWidget.getParent();
            if (container.handlesInternalConstraints()) {
                mDisplayAnchorsPolicy.clear();
                mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.NONE);
            }
        } else {
            // if the parent is NOT a ConstraintWidgetContainer, don't show the anchors
            mDisplayAnchorsPolicy.clear();
            mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.NONE);
        }
        if (!mShowResizeHandles) {
            mDisplayAnchorsPolicy.clear();
            mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.CONNECTED);
        }
    }

    /**
     * Update the show anchors policy. Used for unselected widgets.
     *
     * @param selectedWidget the current selected widget (if any)
     * @param selectedAnchor the current selected anchor (if any)
     */
    public void updateShowAnchorsPolicy(ConstraintWidget selectedWidget,
            ConstraintAnchor selectedAnchor) {
        mDisplayAnchorsPolicy.clear();
        mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.NONE);
        if (getLook() != ColorTheme.Look.HIGHLIGHTED) {
            return;
        }
        if (isShowAllConstraints()) {
            if (mWidget.getParent() != null) {
                // we should only show the constraints anchors if our parent doesn't handle
                // the constraints already
                if (mWidget.getParent() instanceof ConstraintWidgetContainer) {
                    ConstraintWidgetContainer container =
                      (ConstraintWidgetContainer)mWidget.getParent();
                    if (!container.handlesInternalConstraints()) {
                        mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.CONNECTED);
                    }
                }
            } else {
                mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.CONNECTED);
            }
        }
        if (selectedWidget != null) {
            if (!isShowAllConstraints()) {
                mDisplayAnchorsPolicy.clear();
                mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.NONE);
            }
            ConstraintAnchor left =
                    isConnectedAnchor(selectedWidget, ConstraintAnchor.Type.LEFT, mWidget);
            ConstraintAnchor right =
                    isConnectedAnchor(selectedWidget, ConstraintAnchor.Type.RIGHT, mWidget);
            ConstraintAnchor top =
                    isConnectedAnchor(selectedWidget, ConstraintAnchor.Type.TOP, mWidget);
            ConstraintAnchor bottom =
                    isConnectedAnchor(selectedWidget, ConstraintAnchor.Type.BOTTOM, mWidget);
            ConstraintAnchor baseline =
                    isConnectedAnchor(selectedWidget, ConstraintAnchor.Type.BASELINE, mWidget);
            updateDisplayAnchorSet(mDisplayAnchorsPolicy, left);
            updateDisplayAnchorSet(mDisplayAnchorsPolicy, top);
            updateDisplayAnchorSet(mDisplayAnchorsPolicy, right);
            updateDisplayAnchorSet(mDisplayAnchorsPolicy, bottom);
            updateDisplayAnchorSet(mDisplayAnchorsPolicy, baseline);
        }
        if (selectedAnchor != null) {
            if (selectedAnchor.isConnectionAllowed(mWidget)) {
                if (selectedAnchor.getType() == ConstraintAnchor.Type.BASELINE) {
                    mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.BASELINE);
                } else if (selectedAnchor.getType() == ConstraintAnchor.Type.CENTER) {
                    mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.VERTICAL);
                    mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.HORIZONTAL);
                    if (mWidget == selectedAnchor.getOwner().getParent()) {
                        // only display the center anchor for the parent of the selected widget
                        mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.CENTER);
                    }
                } else if (selectedAnchor.isVerticalAnchor()) {
                    mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.VERTICAL);
                } else {
                    mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.HORIZONTAL);
                }
            }
        }
        if (getLook() == ColorTheme.Look.HIGHLIGHTED) {
            mDisplayAnchorsPolicy.clear();
            mDisplayAnchorsPolicy.add(WidgetDraw.ANCHORS_DISPLAY.CONNECTED);
        }
    }

    /**
     * Check if a given anchor from the selected widget is connected to widget
     *
     * @param selectedWidget the widget we are looking at
     * @param type           the type of constraint anchor we are checking
     * @param widget         the widget we want to know if we connect to
     * @return true if the selectedWidget connects to widget via the given anchor type
     */
    private ConstraintAnchor isConnectedAnchor(ConstraintWidget selectedWidget,
            ConstraintAnchor.Type type,
            ConstraintWidget widget) {
        ConstraintAnchor anchor = selectedWidget.getAnchor(type);
        if (anchor != null && anchor.isConnected() && anchor.getTarget().getOwner() == widget) {
            return anchor.getTarget();
        }
        return null;
    }

    /**
     * Set the given anchor display depending on the type of anchor
     *
     * @param set    the EnumSet encoding which anchors to display
     * @param anchor the anchor we connect to that we thus want to display...
     */
    private void updateDisplayAnchorSet(EnumSet<WidgetDraw.ANCHORS_DISPLAY> set,
            ConstraintAnchor anchor) {
        if (anchor == null) {
            return;
        }
        ConstraintAnchor.Type type = anchor.getType();
        switch (type) {
            case LEFT: {
                set.add(WidgetDraw.ANCHORS_DISPLAY.LEFT);
            }
            break;
            case TOP: {
                set.add(WidgetDraw.ANCHORS_DISPLAY.TOP);
            }
            break;
            case RIGHT: {
                set.add(WidgetDraw.ANCHORS_DISPLAY.RIGHT);
            }
            break;
            case BOTTOM: {
                set.add(WidgetDraw.ANCHORS_DISPLAY.BOTTOM);
            }
            break;
            case BASELINE: {
                set.add(WidgetDraw.ANCHORS_DISPLAY.BASELINE);
            }
            break;
        }
    }

    /**
     * Can be overriden by subclasses to apply the dimension behaviour of the widget
     * (i.e., wrap_content, fix, any..)
     */
    public void applyDimensionBehaviour() {
    }

    /**
     * Can be overriden by subclasses to handle mouse press events
     *
     * @param x         mouse x coordinate
     * @param y         mouse y coordinate
     * @param transform view transform
     * @param selection the current selection of widgets
     */
    public ConstraintWidget mousePressed(float x, float y, ViewTransform transform,
            Selection selection) {
        return null;
    }

    /**
     * Can be overriden by subclasses to handle mouse release events
     *
     * @param x         mouse x coordinate
     * @param y         mouse y coordinate
     * @param transform view transform
     * @param selection the current selection of widgets
     */
    public void mouseRelease(int x, int y, ViewTransform transform, Selection selection) {
    }

    public int getStyle() {
        return mStyle;
    }

    public void setStyle(int style) {
        mStyle = style;
    }

    /**
     * Make the actions appear
     */
    private void showActions() {
        if (mColorSet == null) {
            return;
        }
        int delay = 0;
        for (WidgetAction action : mWidgetActions) {
            action.show(delay);
            delay += 250;
        }
        repaint();
    }

    /*-----------------------------------------------------------------------*/
    // WidgetAction implementation
    /*-----------------------------------------------------------------------*/

    /**
     * Base class implementing Widget actions
     */
    public class WidgetAction {

        private int mX;
        private int mY;
        private boolean mOver = false;
        protected final ConstraintWidget mWidget;
        private long mStartVisible = 0;

        WidgetAction(ConstraintWidget widget) {
            mWidget = widget;
        }

        /**
         * Reimplement to draw a tooltip
         *
         * @return an array of strings to display (supporting multi-lines)
         */
        String[] getText() {
            return null;
        }

        /**
         * Called before paint
         */
        void update() {
        }

        /**
         * Return true if the action is visible
         *
         * @return true if the action is visible
         */
        public boolean isVisible() {
            return true;
        }

        /**
         * Return true if the click modified the widget
         *
         * @return true if the click modified the widget
         */
        public boolean click() {
            return false;
        }

        void show(int delay) {
            mStartVisible = System.currentTimeMillis();
        }

        boolean onPaint(ViewTransform transform, Graphics2D g, int x, int y) {
            int r = ACTION_SIZE;
            mX = x;
            mY = y;
            x += r / 2;
            y += r / 2;
            Color pre = g.getColor();
            if (!mOver) {
                g.setColor(mColorSet.getWidgetActionBackground());
            } else {
                g.setColor(mColorSet.getWidgetActionSelectedBackground());
            }
            int c = 8;
            // Draw an outline
            Color prec = g.getColor();
            g.setColor(mColorSet.getBackground());
            g.fillRoundRect(x - r / 2 - 2, y - r / 2 - 2, r + 4, r + 4, c, c);
            g.drawRoundRect(x - r / 2 - 2, y - r / 2 - 2, r + 4, r + 4, c, c);
            g.setColor(prec);
            g.fillRoundRect(x - r / 2, y - r / 2, r, r, c, c);
            g.drawRoundRect(x - r / 2, y - r / 2, r, r, c, c);
            g.setColor(pre);
            if (mOver && mStartVisible != 0
                    && (System.currentTimeMillis() - mStartVisible > WidgetDraw.TOOLTIP_DELAY)) {
                WidgetDraw.drawTooltip(g, mColorSet, getText(), x, y, false);
            } else if (!mOver) {
                mStartVisible = 0;
            }
            return true;
        }

        public void over(boolean value) {
            mOver = value;
            if (mOver && mStartVisible == 0) {
                mStartVisible = System.currentTimeMillis();
            }
            int r = ACTION_SIZE;
            repaint(mX - r / 2, mY - r / 2, r, r);
            // If we are over an action button, make sure we continue to show them!
            if (mOver) {
                mShowActions = true;
                mHideActions.stop();
            }
        }

        public void addToPicker(ViewTransform transform, DrawPicker picker) {
            picker.addRect(WidgetAction.this, 0, mX, mY, mX + ACTION_SIZE, mY + ACTION_SIZE);
        }

        public ConstraintWidget getWidget() {
            return mWidget;
        }

    }

    /**
     * Simple action implementing a lock / unlock of the widget constraints
     */
    private class LockWidgetAction extends WidgetAction {

        int mConstraintsCreator = -1;
        String[] mLockConstraints = {
                "Lock Constraints",
                "(unlock constraints are broken",
                "by dragging the widget)"
        };
        String[] mUnlockConstraints = { "Unlock Constraints" };

        LockWidgetAction(ConstraintWidget widget) {
            super(widget);
        }

        @Override
        void update() {
            mConstraintsCreator = getMainConstraintsCreator(mWidget);
        }

        @Override
        public boolean isVisible() {
            return mConstraintsCreator != -1;
        }

        @Override
        String[] getText() {
            if (mConstraintsCreator == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                return mLockConstraints;
            }
            return mUnlockConstraints;
        }

        @Override
        boolean onPaint(ViewTransform transform, Graphics2D g, int x, int y) {
            if (!super.onPaint(transform, g, x, y)) {
                return false;
            }
            int m = 4;
            int rect = ACTION_SIZE - 2 * m;
            if (sLockImageIcon != null) {
                Image image = sUnlockImageIcon;
                if (mConstraintsCreator == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                    image = sLockImageIcon;
                }
                g.drawImage(image, x + m, y + m,
                        rect, rect, null, null);
            }
            return true;
        }

        @Override
        public boolean click() {
            if (mConstraintsCreator == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                setConstraintsCreator(mWidget, ConstraintAnchor.USER_CREATOR);
            } else {
                setConstraintsCreator(mWidget, ConstraintAnchor.AUTO_CONSTRAINT_CREATOR);
            }
            repaint();
            return true;
        }

        void setConstraintsCreator(ConstraintWidget widget, int creator) {
            for (ConstraintAnchor anchor : widget.getAnchors()) {
                if (anchor.isConnected()) {
                    anchor.setConnectionCreator(creator);
                }
            }
        }

        int getMainConstraintsCreator(ConstraintWidget widget) {
            int numAuto = 0;
            int numUser = 0;
            for (ConstraintAnchor anchor : widget.getAnchors()) {
                if (anchor.isConnected()) {
                    if (anchor.getConnectionCreator() == ConstraintAnchor.AUTO_CONSTRAINT_CREATOR) {
                        numAuto++;
                    } else {
                        numUser++;
                    }
                }
            }
            if (numAuto == 0 && numUser == 0) {
                return -1;
            }
            if (numUser > numAuto) {
                return ConstraintAnchor.USER_CREATOR;
            }
            return ConstraintAnchor.AUTO_CONSTRAINT_CREATOR;
        }

    }

    /**
     * Action implementing a deletion of all constraints of the widget
     */
    private class DeleteConnectionsWidgetAction extends WidgetAction {
        String[] mDeleteText = { "Delete All Constraints" };
        boolean mIsVisible = false;

        DeleteConnectionsWidgetAction(ConstraintWidget widget) {
            super(widget);
        }

        @Override
        String[] getText() {
            return mDeleteText;
        }

        @Override
        void update() {
            mIsVisible = false;
            for (ConstraintAnchor anchor : mWidget.getAnchors()) {
                if (anchor.isConnected()) {
                    mIsVisible = true;
                    return;
                }
            }
        }

        @Override
        public boolean isVisible() {
            return mIsVisible;
        }

        @Override
        boolean onPaint(ViewTransform transform, Graphics2D g, int x, int y) {
            if (!super.onPaint(transform, g, x, y)) {
                return false;
            }
            int m = 4;
            int rect = ACTION_SIZE - 2 * m;
            if (sDeleteConnectionsImageIcon != null) {
                g.drawImage(sDeleteConnectionsImageIcon, x + m, y + m,
                        rect, rect, null, null);
            }
            return true;
        }

        @Override
        public boolean click() {
            mWidget.resetAllConstraints();
            WidgetCompanion companion = (WidgetCompanion)mWidget.getCompanionWidget();
            WidgetDecorator decorator = companion.getWidgetDecorator(BLUEPRINT_STYLE);
            decorator.getStateModel().save(decorator);
            repaint();
            return true;
        }

    }

    /**
     * Action implementing a deletion of all constraints of the widget
     */
    private class ToggleChainStyleWidgetAction extends WidgetAction {
        String[] mPackChainText = { "Toggle chain style" };
        boolean mIsVisible = false;

        ToggleChainStyleWidgetAction(ConstraintWidget widget) {
            super(widget);
        }

        @Override
        String[] getText() {
            return mPackChainText;
        }

        @Override
        void update() {
            mIsVisible = mWidget.isInVerticalChain() || mWidget.isInHorizontalChain();
        }

        @Override
        public boolean isVisible() {
            return mIsVisible;
        }

        @Override
        boolean onPaint(ViewTransform transform, Graphics2D g, int x, int y) {
            if (!super.onPaint(transform, g, x, y)) {
                return false;
            }
            int m = 4;
            int rect = ACTION_SIZE - 2 * m;
            if (sPackChainImageIcon != null) {
                g.drawImage(sPackChainImageIcon, x + m, y + m,
                            rect, rect, null, null);
            }
            return true;
        }

        @Override
        public boolean click() {
            if (mWidget.isInHorizontalChain()) {
                ConstraintWidget tmp = mWidget;
                ConstraintWidget found = null;
                while (found == null && tmp != null) {
                    ConstraintAnchor anchor = tmp.getAnchor(ConstraintAnchor.Type.LEFT);
                    ConstraintAnchor targetOwner = (anchor==null)?null:anchor.getTarget();
                    ConstraintWidget target = (targetOwner==null)?null:targetOwner.getOwner();
                    if (target == mWidget.getParent()) {
                        found = tmp;
                        break;
                    }
                    ConstraintAnchor targetAnchor = (target == null)?null: target.getAnchor(ConstraintAnchor.Type.RIGHT).getTarget();
                    if (targetAnchor != null && targetAnchor.getOwner() != tmp) {
                        found = tmp;
                    } else {
                        tmp = target;
                    }
                }

              if (found != null) {
                  int[] cycle = {ConstraintWidget.CHAIN_SPREAD_INSIDE,ConstraintWidget.CHAIN_PACKED,ConstraintWidget.CHAIN_SPREAD};
                  found.setHorizontalChainStyle(cycle[found.getHorizontalChainStyle()]);
                  WidgetCompanion companion = (WidgetCompanion)found.getCompanionWidget();
                  WidgetDecorator decorator = companion.getWidgetDecorator(BLUEPRINT_STYLE);
                  decorator.getStateModel().save(decorator);
              }
            }
            if (mWidget.isInVerticalChain()) {
                ConstraintWidget tmp = mWidget;
                ConstraintWidget found = null;
                while (found == null && tmp != null) {
                    ConstraintAnchor anchor = tmp.getAnchor(ConstraintAnchor.Type.TOP);
                    ConstraintAnchor targetOwner = (anchor==null)?null:anchor.getTarget();
                    ConstraintWidget target = (targetOwner==null)?null:targetOwner.getOwner();
                    if (target == mWidget.getParent()) {
                        found = tmp;
                        break;
                    }
                    ConstraintAnchor targetAnchor = (target == null)?null: target.getAnchor(ConstraintAnchor.Type.BOTTOM).getTarget();
                    if (targetAnchor != null && targetAnchor.getOwner() != tmp) {
                        found = tmp;
                    } else {
                        tmp = target;
                    }
                }

                if (found != null) {
                    int[] cycle = {ConstraintWidget.CHAIN_SPREAD_INSIDE,ConstraintWidget.CHAIN_PACKED,ConstraintWidget.CHAIN_SPREAD};
                    found.setVerticalChainStyle(cycle[found.getVerticalChainStyle()]);
                    WidgetCompanion companion = (WidgetCompanion)found.getCompanionWidget();
                    WidgetDecorator decorator = companion.getWidgetDecorator(BLUEPRINT_STYLE);
                    decorator.getStateModel().save(decorator);
                }
            }

            return true;
        }

    }

}
