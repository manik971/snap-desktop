/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.visat.actions;

import org.esa.snap.framework.datamodel.ColorPaletteDef;
import org.esa.snap.framework.datamodel.ImageInfo;
import org.esa.snap.framework.datamodel.RasterDataNode;
import org.esa.snap.framework.ui.command.CommandEvent;
import org.esa.snap.framework.ui.command.ExecCommand;
import org.esa.snap.framework.ui.product.ProductSceneView;
import org.esa.snap.jai.ImageManager;
import org.esa.snap.util.PropertyMap;
import org.esa.snap.util.StringUtils;
import org.esa.snap.util.io.FileUtils;
import org.esa.snap.util.io.SnapFileFilter;
import org.esa.snap.visat.VisatApp;

import javax.swing.JFileChooser;
import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This action exports the color palette of the selected product.
 *
 * @author Marco Peters
 * @version $Revision$ $Date$
 */
public class ExportColorPaletteAction extends ExecCommand {

    private static final String KEY_LAST_OPEN = "ExportColorPaletteVPI.path";
    private static final String VPI_TEXT = "Export Color Palette";


    @Override
    public void actionPerformed(CommandEvent event) {
        SnapFileFilter fileFilter1 = new SnapFileFilter("CSV", ".csv", "CSV files"); // I18N
        SnapFileFilter fileFilter2 = new SnapFileFilter("TXT", ".txt", "Text files"); // I18N
        JFileChooser fileChooser = new JFileChooser();
        File lastDir = new File(getPreferences().getPropertyString(KEY_LAST_OPEN, "."));
        fileChooser.setCurrentDirectory(lastDir);
        RasterDataNode raster = getSelectedRaster();
        fileChooser.setSelectedFile(new File(lastDir, raster.getName() + "-palette.csv"));
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.addChoosableFileFilter(fileFilter1);
        fileChooser.addChoosableFileFilter(fileFilter2);
        fileChooser.setFileFilter(fileFilter1);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setDialogTitle(VPI_TEXT);
        if (fileChooser.showSaveDialog(getVisatApp().getMainFrame()) == JFileChooser.APPROVE_OPTION
            && fileChooser.getSelectedFile() != null) {
            getPreferences().setPropertyString(KEY_LAST_OPEN,
                                               fileChooser.getCurrentDirectory().getAbsolutePath());
            File file = fileChooser.getSelectedFile();
            if (fileChooser.getFileFilter() instanceof SnapFileFilter) {
                SnapFileFilter fileFilter = (SnapFileFilter) fileChooser.getFileFilter();
                file = FileUtils.ensureExtension(file, fileFilter.getDefaultExtension());
            }
            try {
                writeColorPalette(raster, file);
            } catch (IOException e) {
                getVisatApp().showErrorDialog(VPI_TEXT, "Failed to export colour palette:\n" + e.getMessage());
            }
        }
    }

    @Override
    public void updateState(CommandEvent event) {
        setEnabled(getSelectedImageInfo() != null);

    }

    private static void writeColorPalette(RasterDataNode raster, File file) throws IOException {
        FileWriter writer = new FileWriter(file);
        try {
            writeColorPalette(raster, writer);
        } finally {
            writer.close();
        }
    }

    private static void writeColorPalette(RasterDataNode raster, FileWriter writer) throws IOException {
        ImageInfo imageInfo = raster.getImageInfo();
        final ColorPaletteDef paletteDef = imageInfo.getColorPaletteDef();
        final Color[] colorPalette = ImageManager.createColorPalette(imageInfo);
//        Color[] colorPalette = paletteDef.createColorPalette(raster);
        double s1 = paletteDef.getMinDisplaySample();
        double s2 = paletteDef.getMaxDisplaySample();
        int numColors = colorPalette.length;
        writer.write("# Band: " + raster.getName() + "\n");
        writer.write("# Sample unit: " + raster.getUnit() + "\n");
        writer.write("# Minimum sample value: " + s1 + "\n");
        writer.write("# Maximum sample value: " + s2 + "\n");
        writer.write("# Number of colors: " + numColors + "\n");
        double sf = (s2 - s1) / (numColors - 1.0);
        writer.write("ID;Sample;RGB\n");
        for (int i = 0; i < numColors; i++) {
            Color color = colorPalette[i];
            double s = s1 + i * sf;
            writer.write(i + ";" + s + ";" + StringUtils.formatColor(color) + "\n");
        }
    }

    private static RasterDataNode getSelectedRaster() {
        ProductSceneView sceneView = getVisatApp().getSelectedProductSceneView();
        if (sceneView != null) {
            return sceneView.getRaster();
        }
        return null;
    }

    private static ImageInfo getSelectedImageInfo() {
        RasterDataNode raster = getSelectedRaster();
        if (raster != null) {
            return raster.getImageInfo();
        }
        return null;
    }

    private static PropertyMap getPreferences() {
        return getVisatApp().getPreferences();
    }

    private static VisatApp getVisatApp() {
        return VisatApp.getApp();
    }

}
