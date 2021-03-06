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

package org.esa.snap.rcp.actions.file.export;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.snap.framework.datamodel.CrsGeoCoding;
import org.esa.snap.framework.datamodel.VectorDataNode;
import org.esa.snap.framework.ui.UIUtils;
import org.esa.snap.jai.ImageManager;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.SnapDialogs;
import org.esa.snap.util.io.SnapFileFilter;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.store.ReprojectingFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;

import javax.swing.AbstractAction;
import javax.swing.SwingWorker;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;


@ActionID(
        category = "File",
        id = "org.esa.snap.rcp.actions.file.export.ExportGeometryAction"
)
@ActionRegistration(
        displayName = "#CTL_ExportGeometryAction_MenuText",
        popupText = "#CTL_ExportGeometryAction_ShortDescription",
        lazy = true
)

@ActionReference(
        path = "Menu/File/Other Exports",
        position = 40
)


@NbBundle.Messages({
        "CTL_ExportGeometryAction_MenuText=Geometry as Shape file",
        "CTL_ExportGeometryAction_ShortDescription=Exports the currently selected geometry as ESRI Shapefile."
})

public class ExportGeometryAction extends AbstractAction implements HelpCtx.Provider{

    private static final String ESRI_SHAPEFILE = "ESRI Shapefile";
    private static final String FILE_EXTENSION_SHAPEFILE = ".shp";
    private static final String DLG_TITLE = "Export Geometry as " + ESRI_SHAPEFILE;
    private static final String HELP_ID= "exportShapefile";
    private VectorDataNode vectorDataNode;


    public ExportGeometryAction(VectorDataNode vectorDataNode) {
        this.vectorDataNode = vectorDataNode;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        exportVectorDataNode();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return new HelpCtx(HELP_ID);
    }

    /////////////////////////////////////////////////////////////////////////
    // Private implementations for the "export Mask Pixels" command
    /////////////////////////////////////////////////////////////////////////

    /**
     * Performs the actual "export Mask Pixels" command.
     */
    private void exportVectorDataNode() {
        SnapApp snapApp = SnapApp.getDefault();
        if (vectorDataNode.getFeatureCollection().isEmpty()) {
           SnapDialogs.showInformation(DLG_TITLE, "The selected geometry is empty. Nothing to export.", null);
            return;
        }

        final File file = promptForFile(snapApp, vectorDataNode.getName());
        if (file == null) {
            return;
        }
        final SwingWorker<Exception, Object> swingWorker = new ExportVectorNodeSwingWorker(snapApp, vectorDataNode, file);

        UIUtils.setRootFrameWaitCursor(snapApp.getMainFrame());
        snapApp.setStatusBarMessage("Exporting Geometry...");

        swingWorker.execute();
    }

    /*
     * Opens a modal file chooser dialog that prompts the user to select the output file name.
     *
     * @param visatApp the VISAT application
     * @return the selected file, <code>null</code> means "Cancel"
     */

    private static File promptForFile(final SnapApp snapApp, String defaultFileName) {
        return SnapDialogs.requestFileForSave(DLG_TITLE, false,
                                new SnapFileFilter(ESRI_SHAPEFILE, FILE_EXTENSION_SHAPEFILE, ESRI_SHAPEFILE),
                                FILE_EXTENSION_SHAPEFILE,
                                defaultFileName,
                                null,
                                "exportVectorDataNode.lastDir");
    }

    private static void exportVectorDataNode(VectorDataNode vectorNode, File file, ProgressMonitor pm) throws
            IOException {


        Map<Class<?>, List<SimpleFeature>> featureListMap = createGeometryToFeaturesListMap(vectorNode);
        if (featureListMap.size() > 1) {
            final String msg = "The selected geometry contains different types of shapes.\n" +
                    "Each type of shape will be exported as a separate shapefile.";
            SnapDialogs.showInformation(DLG_TITLE, msg, ExportGeometryAction.class.getName() + ".exportInfo");
        }

        Set<Map.Entry<Class<?>, List<SimpleFeature>>> entries = featureListMap.entrySet();
        pm.beginTask("Writing ESRI Shapefiles...", featureListMap.size());
        try {
            for (Map.Entry<Class<?>, List<SimpleFeature>> entry : entries) {
                writeEsriShapefile(entry.getKey(), entry.getValue(), file);
                pm.worked(1);
            }
        } finally {
            pm.done();
        }
    }

    public static void writeEsriShapefile(Class<?> geomType, List<SimpleFeature> features, File file) throws
            IOException {
        String geomName = geomType.getSimpleName();
        String basename = file.getName();
        if (basename.endsWith(FILE_EXTENSION_SHAPEFILE)) {
            basename = basename.substring(0, basename.length() - 4);
        }
        File file1 = new File(file.getParentFile(), basename + "_" + geomName + FILE_EXTENSION_SHAPEFILE);

        ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
        Map map = Collections.singletonMap("url", file1.toURI().toURL());
        DataStore dataStore = factory.createNewDataStore(map);
        SimpleFeature simpleFeature = features.get(0);
        SimpleFeatureType simpleFeatureType = changeGeometryType(simpleFeature.getType(), geomType);

        String typeName = simpleFeatureType.getName().getLocalPart();
        dataStore.createSchema(simpleFeatureType);
        FeatureStore<SimpleFeatureType, SimpleFeature> featureStore = (FeatureStore<SimpleFeatureType, SimpleFeature>) dataStore.getFeatureSource(
                typeName);
        DefaultTransaction transaction = new DefaultTransaction("X");
        featureStore.setTransaction(transaction);
        final FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection = DataUtilities.collection(
                features);
        featureStore.addFeatures(featureCollection);
        try {
            transaction.commit();
        } catch (IOException e) {
            transaction.rollback();
            throw e;
        } finally {
            transaction.close();
        }
    }

    private static Map<Class<?>, List<SimpleFeature>> createGeometryToFeaturesListMap(VectorDataNode vectorNode) {
        FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection = vectorNode.getFeatureCollection();
        CoordinateReferenceSystem crs = vectorNode.getFeatureType().getCoordinateReferenceSystem();
        if (crs == null) {   // for pins and GCPs crs is null --> assume image crs
            crs = vectorNode.getProduct().getGeoCoding().getImageCRS();
        }
        final CoordinateReferenceSystem modelCrs;
        if (vectorNode.getProduct().getGeoCoding() instanceof CrsGeoCoding) {
            modelCrs = ImageManager.getModelCrs(vectorNode.getProduct().getGeoCoding());
        } else {
            modelCrs = DefaultGeographicCRS.WGS84;
        }
        if (!CRS.equalsIgnoreMetadata(crs, modelCrs)) { // we have to reproject the features
            featureCollection = new ReprojectingFeatureCollection(featureCollection, crs, modelCrs);
        }
        Map<Class<?>, List<SimpleFeature>> featureListMap = new HashMap<>();
        final FeatureIterator<SimpleFeature> featureIterator = featureCollection.features();
        while (featureIterator.hasNext()) {
            SimpleFeature feature = featureIterator.next();
            Object defaultGeometry = feature.getDefaultGeometry();
            Class<?> geometryType = defaultGeometry.getClass();

            List<SimpleFeature> featureList = featureListMap.get(geometryType);
            if (featureList == null) {
                featureList = new ArrayList<>();
                featureListMap.put(geometryType, featureList);
            }
            featureList.add(feature);
        }
        return featureListMap;
    }

    private static SimpleFeatureType changeGeometryType(SimpleFeatureType original, Class<?> geometryType) {
        SimpleFeatureTypeBuilder sftb = new SimpleFeatureTypeBuilder();
        sftb.setCRS(original.getCoordinateReferenceSystem());
        sftb.setDefaultGeometry(original.getGeometryDescriptor().getLocalName());
        sftb.add(original.getGeometryDescriptor().getLocalName(), geometryType);
        for (AttributeDescriptor descriptor : original.getAttributeDescriptors()) {
            if (!original.getGeometryDescriptor().getLocalName().equals(descriptor.getLocalName())) {
                sftb.add(descriptor);
            }
        }
        sftb.setName("FT_" + geometryType.getSimpleName());
        return sftb.buildFeatureType();
    }




    private static class ExportVectorNodeSwingWorker extends ProgressMonitorSwingWorker<Exception, Object> {

        private final SnapApp snapApp;
        private final VectorDataNode vectorDataNode;
        private final File file;

        private ExportVectorNodeSwingWorker(SnapApp snapApp, VectorDataNode vectorDataNode, File file) {
            super(snapApp.getMainFrame(), ExportGeometryAction.DLG_TITLE);
            this.snapApp = snapApp;
            this.vectorDataNode = vectorDataNode;
            this.file = file;
        }

        @Override
        protected Exception doInBackground(ProgressMonitor pm) throws Exception {
            try {
                exportVectorDataNode(vectorDataNode, file, pm);
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        /**
         * Called on the event dispatching thread (not on the worker thread) after the <code>construct</code> method
         * has returned.
         */
        @Override
        public void done() {
            Exception exception = null;
            try {
                UIUtils.setRootFrameDefaultCursor(SnapApp.getDefault().getMainFrame());
                snapApp.setStatusBarMessage("");
                exception = get();
            } catch (InterruptedException e) {
                exception = e;
            } catch (ExecutionException e) {
                exception = e;
            } finally {
                if (exception != null) {
                    exception.printStackTrace();
                    SnapDialogs.showError(DLG_TITLE, "Can not export geometry.\n" + exception.getMessage());
                }
            }
        }

    }
}
