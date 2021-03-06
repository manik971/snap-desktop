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

package org.esa.snap.framework.param.editors;

import junit.framework.TestCase;
import org.esa.snap.framework.param.ParamEditor;
import org.esa.snap.framework.param.ParamProperties;
import org.esa.snap.framework.param.Parameter;
import org.esa.snap.framework.param.validators.BooleanExpressionValidator;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Component;

/**
 * EditorComponentNames Tester.
 * Ensures that all the components given by any ParamEditor have the same name as the parameters lable text.
 *
 * @author <Sabine Embacher>
 * @version 1.0
 * @since <pre>02/25/2005</pre>
 */
public class ExParamEditorsTest extends TestCase {

    private static final String LABEL_NAME = "This is the label";
    private static final String UNIT_NAME = "This is the unit";
    private static final String PARAM_NAME = "theParamName";


    public void testGetComponentNames_BitmaskExprEditor() {
        final ParamProperties properties = new ParamProperties(String.class);
        properties.setLabel(LABEL_NAME);
        properties.setPhysicalUnit(UNIT_NAME);
        properties.setDefaultValue(Color.red);
        properties.setEditorClass(BooleanExpressionEditor.class);
        properties.setValidatorClass(BooleanExpressionValidator.class);
        final Parameter parameter = new Parameter(PARAM_NAME, properties);
        ParamEditor editor = parameter.getEditor();
        assertTrue(editor instanceof BooleanExpressionEditor);
        final BooleanExpressionEditor bitmaskExprEditor = (BooleanExpressionEditor) editor;

        final JComponent editorComponent = bitmaskExprEditor.getEditorComponent();
        assertEquals("theParamName.XEditor", editorComponent.getName());
        final Component[] components = editorComponent.getComponents();
        assertEquals(2, components.length);
        assertEquals("theParamName.Editor", components[0].getName());
        assertEquals("theParamName.ButtonPanel", components[1].getName());
        assertEquals(JTextField.class.getName(), components[0].getClass().getName());
        assertEquals(JPanel.class.getName(), components[1].getClass().getName());
        final JPanel buttonPanel = ((JPanel) components[1]);
        assertEquals(1, buttonPanel.getComponentCount());
        assertEquals(JButton.class.getName(), buttonPanel.getComponents()[0].getClass().getName());
        assertEquals("theParamName.Label", bitmaskExprEditor.getLabelComponent().getName());
        assertEquals(LABEL_NAME + ": ", bitmaskExprEditor.getLabelComponent().getText());
        assertEquals("theParamName.Unit", bitmaskExprEditor.getPhysUnitLabelComponent().getName());
        assertEquals(" " + UNIT_NAME, bitmaskExprEditor.getPhysUnitLabelComponent().getText());
    }

}
