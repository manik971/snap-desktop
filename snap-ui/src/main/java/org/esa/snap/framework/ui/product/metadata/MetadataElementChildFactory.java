package org.esa.snap.framework.ui.product.metadata;

import org.esa.snap.framework.datamodel.MetadataAttribute;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Tonio Fincke
 */
public class MetadataElementChildFactory extends ChildFactory.Detachable<MetadataTableElement> {

    private List<MetadataTableElement> metadataTableElementList;

    public MetadataElementChildFactory(MetadataTableElement[] metadataElements) {
        metadataTableElementList = new ArrayList<>();
        Collections.addAll(metadataTableElementList, metadataElements);
    }

    @Override
    protected boolean createKeys(List<MetadataTableElement> toPopulate) {
        return toPopulate.addAll(metadataTableElementList);
    }

    @Override
    protected Node createNodeForKey(MetadataTableElement metadataElement) {
        if (metadataElement instanceof MetadataElementWrapper) {
            return new MetadataElementInnerNode(metadataElement);
        } else {
//            MetadataAttribute attribute = (MetadataAttribute) metadataElement;
//            if (attribute.getDataElemSize() > 0) {
//
//            }
            return new MetadataElementLeafNode((MetadataAttribute) metadataElement);
        }
    }

}