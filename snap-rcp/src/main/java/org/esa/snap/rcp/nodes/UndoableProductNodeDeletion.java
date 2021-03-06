package org.esa.snap.rcp.nodes;

import com.bc.ceres.core.Assert;
import org.esa.snap.framework.datamodel.ProductNode;
import org.esa.snap.framework.datamodel.ProductNodeGroup;
import org.openide.util.NbBundle;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

/**
 * An undoable insertion of a {@code ProductNode}.
 *
 * @param <T> The product node type.
 * @author Norman Fomferra
 */
@NbBundle.Messages("LBL_UndoableProductNodeDeletionName=Delete ''{0}''")
public class UndoableProductNodeDeletion<T extends ProductNode> extends AbstractUndoableEdit {

    private ProductNodeGroup<T> productNodeGroup;
    private T productNode;
    private final int index;

    public UndoableProductNodeDeletion(ProductNodeGroup<T> productNodeGroup, T productNode, int index) {
        Assert.notNull(productNodeGroup, "group");
        Assert.notNull(productNode, "node");
        this.productNodeGroup = productNodeGroup;
        this.productNode = productNode;
        this.index = index;
    }

    public T getProductNode() {
        return productNode;
    }

    @Override
    public String getPresentationName() {
        return Bundle.LBL_UndoableProductNodeDeletionName(productNode.getName());
    }

    @Override
    public void undo() throws CannotUndoException {
        super.undo();
        if (index < productNodeGroup.getNodeCount()) {
            productNodeGroup.add(productNode);
        } else {
            productNodeGroup.add(index, productNode);
        }
    }

    @Override
    public void redo() throws CannotRedoException {
        super.redo();
        // todo - close all open document windows
        productNodeGroup.remove(productNode);
    }

    @Override
    public void die() {
        productNodeGroup = null;
        productNode = null;
    }
}
