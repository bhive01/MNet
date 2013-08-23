package com.imagej.plugins.melons;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.ImageProcessor;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Arrays;

/**
 * Filter for detecting melon netting.
 *
 * @author $Author$
 * @version $Revision$
 */
public class Netting
        implements ExtendedPlugInFilter, DialogListener
{
    // RGB colours.
    private static final int BLUE_RGB = 0x0000ff;
    private static final int WHITE_RGB = 0xffffff;

    // Filter flags.
    private static final int FLAGS = DOES_RGB | PARALLELIZE_IMAGES | FINAL_PROCESSING;

    // Parameters.
    private static final String ANGLE_STEP_PARAMETER = "Angle Step";
    private static final int ANGLE_STEP_DEFAULT = 30;
    private static final int ANGLE_STEP_MIN = 1;
    private static final int ANGLE_STEP_MAX = 180;
    private static final String SENSITIVITY_PARAMETER = "Sensitivity";
    private static final int SENSITIVITY_DEFAULT = 10;
    private static final int SENSITIVITY_MIN = 0;
    private static final int SENSITIVITY_MAX = 255;
    private static final String RADIUS_PARAMETER = "Radius";
    private static final int RADIUS_DEFAULT = 50;
    private static final int RADIUS_MIN = 1;
    private static final int RADIUS_MAX = 100;
    private static final String BACKGROUND_PARAMETER = "Background Cutoff";
    private static final int BACKGROUND_MIN = 0;
    private static final int BACKGROUND_MAX = 255;
    private static final int BACKGROUND_DEFAULT = 20;
    private static final String NETTING_PARAMETER = "Netting Mean Cutoff";
    private static final int NETTING_MIN = 0;
    private static final int NETTING_MAX = 255;
    private static final int NETTING_DEFAULT = 110;

    // Parameters.
    private int angleStep;
    private int radius;
    private int sensitivity;
    private int background;
    private int netting;

    // Holds processing results.
    private int[] pixels;
    private int[] results;

    // Progress counter.
    private int progress;

    /**
     * Create filter.
     */
    public Netting()
    {
        // Initialize.
        pixels = null;
        results = null;
        progress = 0;
        angleStep = ANGLE_STEP_DEFAULT;
        radius = RADIUS_DEFAULT;
        sensitivity = SENSITIVITY_DEFAULT;
        background = BACKGROUND_DEFAULT;
        netting = NETTING_DEFAULT;
    }

    @Override
    public int setup(final String arg,
                     final ImagePlus imp)
    {
        if ("about".equalsIgnoreCase(arg))
        {
            showAbout();
            return DONE;
        }

        if ("final".equalsIgnoreCase(arg))
        {
            imp.getProcessor().setPixels(results);
        }

        return FLAGS;
    }

    @Override
    public int showDialog(final ImagePlus imp,
                          final String command,
                          final PlugInFilterRunner pfr)
    {
        // Create storage for results.
        pixels = (int[]) imp.getProcessor().getPixelsCopy();
        results = new int[pixels.length];
        Arrays.fill(results, WHITE_RGB);

        // Create dialog.
        final GenericDialog dialog = new GenericDialog(command);
        dialog.addSlider(ANGLE_STEP_PARAMETER, (double) ANGLE_STEP_MIN, (double) ANGLE_STEP_MAX, (double) angleStep);
        dialog.addSlider(RADIUS_PARAMETER, (double) RADIUS_MIN, (double) RADIUS_MAX, (double) radius);
        dialog.addSlider(SENSITIVITY_PARAMETER, (double) SENSITIVITY_MIN, (double) SENSITIVITY_MAX,
                         (double) sensitivity);
        dialog.addSlider(BACKGROUND_PARAMETER, (double) BACKGROUND_MIN, (double) BACKGROUND_MAX, (double) background);
        dialog.addSlider(NETTING_PARAMETER, (double) NETTING_MIN, (double) NETTING_MAX, (double) netting);
        dialog.addPreviewCheckbox(pfr);
        dialog.addDialogListener(this);

        // Listen to preview checkbox.
        final Checkbox checkbox = dialog.getPreviewCheckbox();
        checkbox.addItemListener(new ItemListener()
        {
            @Override
            public void itemStateChanged(final ItemEvent itemEvent)
            {
                imp.getProcessor().setPixels(itemEvent.getStateChange() == ItemEvent.SELECTED ? results : pixels);
                imp.updateAndDraw();
            }
        });

        // Display dialog.
        dialog.showDialog();
        if (dialog.wasCanceled())
        {
            imp.getProcessor().setSnapshotPixels(pixels);
            return DONE;
        }
        else
        {
            return IJ.setupDialog(imp, FLAGS);
        }
    }

    @Override
    public boolean dialogItemChanged(final GenericDialog gd,
                                     final AWTEvent e)
    {
        // Get parameter values.
        angleStep = (int) gd.getNextNumber();
        radius = (int) gd.getNextNumber();
        sensitivity = (int) gd.getNextNumber();
        background = (int) gd.getNextNumber();
        netting = (int) gd.getNextNumber();

        // Reset progress counter.
        progress = 0;

        return angleStep >= ANGLE_STEP_MIN && angleStep <= ANGLE_STEP_MAX
               && radius >= RADIUS_MIN && radius <= RADIUS_MAX
               && sensitivity >= SENSITIVITY_MIN && sensitivity <= SENSITIVITY_MAX
               && background >= BACKGROUND_MIN && background <= BACKGROUND_MAX
               && netting >= NETTING_MIN && netting <= NETTING_MAX;
    }

    @Override
    public void run(final ImageProcessor ip)
    {
        IJ.showStatus("Detect netting (angle step=" + angleStep
                      + "; radius=" + radius
                      + "; sensitivity=" + sensitivity
                      + "; background=" + background
                      + "; netting=" + netting + ')');

        // Initialize.
        final int width = ip.getWidth();
        final int height = ip.getHeight();

        // Loop through ROI see PARALLELIZE_IMAGES.
        final Rectangle roi = ip.getRoi();
        final int iStart = Math.max(roi.y, radius);
        final int iStop = Math.min(roi.y + roi.height, height - radius);
        final int jStart = roi.x + radius;
        final int jStop = roi.x + roi.width - radius;
        for (int i = iStart;
             i < iStop;
             i++)
        {
            final int offset = i * width;
            for (int j = jStart;
                 j < jStop;
                 j++)
            {
                final int pos = offset + j;
                results[pos] = isNetting(width, pos) ? 0 : WHITE_RGB;
            }

            // Update progress - should be synchronized due to {@code PARALLELIZE_IMAGES} but not really dangerous to do
            // un-synchronized so avoid the performance hit.
            IJ.showProgress(progress++, height);

            // Interrupted? E.g. during preview mode when the user has entered new values.
            if (Thread.currentThread().isInterrupted())
            {
                return;
            }
        }
    }

    /**
     * Tests whether a pixel value is netting.
     *
     * @param width image width.
     * @param pos   index into the pixel array.
     * @return True/false if the pixel's value represents netting.
     */
    private boolean isNetting(final int width,
                              final int pos)
    {
        boolean isNetting = false;
        final int val = pixels[pos] & BLUE_RGB;
        if (val >= background)
        {
            // Loop through angles - exit early if netting is found.
            for (int degrees = 0;
                 !isNetting && degrees < 360;
                 degrees += angleStep)
            {
                // Determine mean pixel intensity along angled ray.
                int sum = 0;
                int count = 0;
                for (int r = 0;
                     r < radius;
                     r++)
                {
                    final double rad = Math.toRadians((double) degrees);
                    final double len = (double) r;
                    final int x = (int) (StrictMath.cos(rad) * len);
                    final int y = (int) (StrictMath.sin(rad) * len);
                    final int z = pixels[pos + x + y * width] & BLUE_RGB;
                    if (z > background) {
                        sum += z;
                        count++;
                    }
                }
 
                // Calculate mean.
                final double meanIntensity = (double) sum / (double) count;

                // Test pixel intensity is greater than mean.
                isNetting = meanIntensity >= (double) netting && (double) val - meanIntensity > (double) sensitivity;
            }
        }
        return isNetting;
    }

    @Override
    public void setNPasses(final int nPasses)
    {
        // ignored.
    }

    private static void showAbout()
    {
        IJ.showMessage("Melon Netting v0.0.1",
                       "<html>Authors<br/>" +
                       "Rob Lind (rob.lind@syngenta.com)<br/>" +
                       "Brandon Hurr (brandon.hurr@syngenta.com)<br/>" +
                       "Chris Pudney (chris.pudney@syngenta.com)</html>");
    }
}
