/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.snap.worldwind.layers;

import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.render.Polyline;
import gov.nasa.worldwind.render.SurfaceImage;
import org.esa.snap.datamodel.AbstractMetadata;
import org.esa.snap.eo.Constants;
import org.esa.snap.eo.GeoUtils;
import org.esa.snap.framework.dataio.ProductSubsetDef;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.GeoCoding;
import org.esa.snap.framework.datamodel.GeoPos;
import org.esa.snap.framework.datamodel.MetadataElement;
import org.esa.snap.framework.datamodel.PixelPos;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.RasterDataNode;
import org.esa.snap.framework.gpf.GPF;
import org.esa.snap.gpf.OperatorUtils;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.util.ProductUtils;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
    Default Product Layer draws product outline
 */
public class DefaultProductLayer extends BaseLayer implements WWLayer {

    private boolean enableSurfaceImages;

    private final ConcurrentHashMap<String, Polyline[]> outlineTable = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SurfaceImage> imageTable = new ConcurrentHashMap<>();

    public WorldWindowGLCanvas theWWD = null;

    public DefaultProductLayer() {
        this.setName("Products");
    }

    public void setEnableSurfaceImages(final boolean enableSurfaceImages) {
        this.enableSurfaceImages = enableSurfaceImages;
    }

    public String[] getProductNames() {
        final List<String> list = new ArrayList<>(outlineTable.keySet());
        Collections.sort(list);
        return list.toArray(new String[outlineTable.size()]);
    }

    private static String getUniqueName(final Product product) {
        return product.getProductRefString() + product.getName();
    }

    @Override
    public void setOpacity(double opacity) {
        super.setOpacity(opacity);

        for (Map.Entry<String, SurfaceImage> entry : this.imageTable.entrySet()) {
            entry.getValue().setOpacity(opacity);
        }
    }

    public void setOpacity(String name, double opacity) {
        final SurfaceImage img = imageTable.get(name);
        if (img != null)
            img.setOpacity(opacity);
    }

    public double getOpacity(String name) {
        final SurfaceImage img = imageTable.get(name);
        if (img != null)
            return img.getOpacity();
        else {
            final Polyline[] lineList = outlineTable.get(name);
            return lineList != null ? 1 : 0;
        }
    }

    public void updateInfoAnnotation(final SelectEvent event) {

    }

    @Override
    public void setSelectedProduct(final Product product) {
        super.setSelectedProduct(product);

        if (selectedProduct != null) {
            final String selName = getUniqueName(selectedProduct);
            for (String name : outlineTable.keySet()) {
                final Polyline[] lineList = outlineTable.get(name);
                final boolean highlight = name.equals(selName);
                for (Polyline line : lineList) {
                    line.setHighlighted(highlight);
                    line.setHighlightColor(Color.RED);
                }
            }
        }
    }

    public void addProduct(final Product product, WorldWindowGLCanvas wwd) {
        theWWD = wwd;

        final String name = getUniqueName(product);
        if (this.outlineTable.get(name) != null)
            return;

        final GeoCoding geoCoding = product.getGeoCoding();
        if (geoCoding == null) {
            final String productType = product.getProductType();
            if (productType.equals("ASA_WVW_2P") || productType.equals("ASA_WVS_1P") || productType.equals("ASA_WVI_1P")) {
                addWaveProduct(product);
            }
        } else {

            if (enableSurfaceImages)
                addSurfaceImage(product);

            // add outline
            addOutline(product);
        }
    }

    private void addSurfaceImage(final Product product) {
        final String name = getUniqueName(product);

        final SwingWorker worker = new SwingWorker() {

            @Override
            protected SurfaceImage doInBackground() throws Exception {
                try {
                    final Product newProduct = createSubsampledProduct(product);
                    final Band band = newProduct.getBandAt(0);
                    final BufferedImage image = ProductUtils.createRgbImage(new RasterDataNode[]{band},
                                                                            band.getImageInfo(com.bc.ceres.core.ProgressMonitor.NULL),
                                                                            com.bc.ceres.core.ProgressMonitor.NULL);

                    final GeoPos geoPos1 = product.getGeoCoding().getGeoPos(new PixelPos(0, 0), null);
                    final GeoPos geoPos2 = product.getGeoCoding().getGeoPos(new PixelPos(product.getSceneRasterWidth() - 1,
                                                                                         product.getSceneRasterHeight() - 1),
                                                                            null
                    );

                    final Sector sector = new Sector(Angle.fromDegreesLatitude(geoPos1.getLat()),
                                                     Angle.fromDegreesLatitude(geoPos2.getLat()),
                                                     Angle.fromDegreesLongitude(geoPos1.getLon()),
                                                     Angle.fromDegreesLongitude(geoPos2.getLon()));

                    final SurfaceImage si = new SurfaceImage(image, sector);
                    si.setOpacity(getOpacity());
                    return si;
                } catch (Exception e) {
                    //e.printStackTrace();
                }
                return null;
            }

            @Override
            public void done() {

                try {
                    if (imageTable.contains(name))
                        removeImage(name);
                    final SurfaceImage si = (SurfaceImage) get();
                    if(si != null) {
                        addRenderable(si);
                        imageTable.put(name, si);
                    }
                } catch (Exception e) {
                    SnapDialogs.showError(e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void addOutline(final Product product) {

        final int step = Math.max(16, (product.getSceneRasterWidth() + product.getSceneRasterHeight()) / 250);
        final GeneralPath[] boundaryPaths = ProductUtils.createGeoBoundaryPaths(product, null, step);

        final Polyline[] polyLineList = new Polyline[boundaryPaths.length];
        int i = 0;
        for (GeneralPath boundaryPath : boundaryPaths) {
            final PathIterator it = boundaryPath.getPathIterator(null);
            final float[] floats = new float[2];
            final List<Position> positions = new ArrayList<>(4);

            it.currentSegment(floats);
            final Position firstPosition = new Position(Angle.fromDegreesLatitude(floats[1]),
                    Angle.fromDegreesLongitude(floats[0]), 0.0);
            positions.add(firstPosition);
            it.next();

            while (!it.isDone()) {
                it.currentSegment(floats);
                positions.add(new Position(Angle.fromDegreesLatitude(floats[1]),
                        Angle.fromDegreesLongitude(floats[0]), 0.0));
                it.next();
            }
            // close the loop
            positions.add(firstPosition);

            polyLineList[i] = new Polyline();
            polyLineList[i].setFollowTerrain(true);
            polyLineList[i].setPositions(positions);

            // ADDED
            //polyLineList[i].setColor(new Color(1f, 0f, 0f, 0.99f));
            //polyLineList[i].setLineWidth(10);

            addRenderable(polyLineList[i]);
            ++i;
        }
        outlineTable.put(getUniqueName(product), polyLineList);
    }

    private void addWaveProduct(final Product product) {
        final MetadataElement root = AbstractMetadata.getOriginalProductMetadata(product);
        final MetadataElement ggADS = root.getElement("GEOLOCATION_GRID_ADS");
        if (ggADS == null) return;

        final MetadataElement[] geoElemList = ggADS.getElements();
        final Polyline[] lineList = new Polyline[geoElemList.length];
        int cnt = 0;
        for (MetadataElement geoElem : geoElemList) {
            final double lat = geoElem.getAttributeDouble("center_lat", 0.0) / Constants.oneMillion;
            final double lon = geoElem.getAttributeDouble("center_long", 0.0) / Constants.oneMillion;
            final double heading = geoElem.getAttributeDouble("heading", 0.0);

            final GeoUtils.LatLonHeading r1 = GeoUtils.vincenty_direct(lon, lat, 5000, heading);
            final GeoUtils.LatLonHeading corner1 = GeoUtils.vincenty_direct(r1.lon, r1.lat, 2500, heading - 90.0);
            final GeoUtils.LatLonHeading corner2 = GeoUtils.vincenty_direct(r1.lon, r1.lat, 2500, heading + 90.0);

            final GeoUtils.LatLonHeading r2 = GeoUtils.vincenty_direct(lon, lat, 5000, heading + 180.0);
            final GeoUtils.LatLonHeading corner3 = GeoUtils.vincenty_direct(r2.lon, r2.lat, 2500, heading - 90.0);
            final GeoUtils.LatLonHeading corner4 = GeoUtils.vincenty_direct(r2.lon, r2.lat, 2500, heading + 90.0);

            final List<Position> positions = new ArrayList<>(4);
            positions.add(new Position(Angle.fromDegreesLatitude(corner1.lat), Angle.fromDegreesLongitude(corner1.lon), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(corner2.lat), Angle.fromDegreesLongitude(corner2.lon), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(corner4.lat), Angle.fromDegreesLongitude(corner4.lon), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(corner3.lat), Angle.fromDegreesLongitude(corner3.lon), 0.0));
            positions.add(new Position(Angle.fromDegreesLatitude(corner1.lat), Angle.fromDegreesLongitude(corner1.lon), 0.0));

            final Polyline line = new Polyline();
            line.setFollowTerrain(true);
            line.setPositions(positions);

            addRenderable(line);
            lineList[cnt++] = line;
        }
        outlineTable.put(getUniqueName(product), lineList);
    }

    public void removeProduct(final Product product) {
        removeOutline(getUniqueName(product));
        removeImage(getUniqueName(product));
    }

    private void removeOutline(String imagePath) {
        final Polyline[] lineList = this.outlineTable.get(imagePath);
        if (lineList != null) {
            for (Polyline line : lineList) {
                this.removeRenderable(line);
            }
            this.outlineTable.remove(imagePath);
        }
    }

    private void removeImage(String imagePath) {
        final SurfaceImage si = this.imageTable.get(imagePath);
        if (si != null) {
            this.removeRenderable(si);
            this.imageTable.remove(imagePath);
        }
    }

    private static Product createSubsampledProduct(final Product product) throws IOException {

        final String quicklookBandName = ProductUtils.findSuitableQuicklookBandName(product);
        final ProductSubsetDef productSubsetDef = new ProductSubsetDef("subset");
        int scaleFactor = product.getSceneRasterWidth() / 1000;
        if (scaleFactor < 1) {
            scaleFactor = 1;
        }
        productSubsetDef.setSubSampling(scaleFactor, scaleFactor);
        productSubsetDef.setTreatVirtualBandsAsRealBands(true);
        productSubsetDef.setNodeNames(new String[]{quicklookBandName});
        Product productSubset = product.createSubset(productSubsetDef, quicklookBandName, null);

        if (!OperatorUtils.isMapProjected(product) && productSubset.getGeoCoding() != null) {
            try {
                final Map<String, Object> projParameters = new HashMap<>();
                Map<String, Product> projProducts = new HashMap<>();
                projProducts.put("source", productSubset);
                projParameters.put("crs", "WGS84(DD)");
                productSubset = GPF.createProduct("Reproject", projParameters, projProducts);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return productSubset;
    }

    public JPanel getControlPanel(final WorldWindowGLCanvas wwd) {
        final JSlider opacitySlider = new JSlider();
        opacitySlider.setMaximum(100);
        opacitySlider.setValue((int) (getOpacity() * 100));
        opacitySlider.setEnabled(true);
        opacitySlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int value = opacitySlider.getValue();
                setOpacity(value / 100d);
                wwd.repaint();
            }
        });

        //theSelectedObjectLabel = new JLabel("Selected: ");

        final JPanel opacityPanel = new JPanel(new BorderLayout(5, 5));
        opacityPanel.add(new JLabel("Opacity"), BorderLayout.WEST);
        opacityPanel.add(opacitySlider, BorderLayout.CENTER);
        return opacityPanel;
    }
}

