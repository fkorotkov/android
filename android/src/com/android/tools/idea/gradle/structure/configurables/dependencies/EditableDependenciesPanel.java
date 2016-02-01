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
package com.android.tools.idea.gradle.structure.configurables.dependencies;

import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyEditor;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleEditor;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.TableView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

/**
 * Panel that displays the table of "editable" dependencies.
 */
class EditableDependenciesPanel extends JPanel {
  @NotNull private final PsdAndroidModuleEditor myModuleEditor;
  private final TableView<PsdAndroidDependencyEditor> myDependencyTable;

  EditableDependenciesPanel(@NotNull PsdAndroidModuleEditor moduleEditor) {
    super(new BorderLayout());
    myModuleEditor = moduleEditor;

    List<PsdAndroidDependencyEditor> dependencies = myModuleEditor.getDeclaredDependencies();
    EditableDependenciesTableModel tableModel = new EditableDependenciesTableModel(dependencies);

    myDependencyTable = new TableView<PsdAndroidDependencyEditor>(tableModel);
    if (!dependencies.isEmpty()) {
      myDependencyTable.changeSelection(0, 0, false, false);
    }

    setUpUI();
  }

  private void setUpUI() {
    myDependencyTable.setDragEnabled(false);
    myDependencyTable.setIntercellSpacing(new Dimension(0, 0));
    myDependencyTable.setShowGrid(false);

    myDependencyTable.getSelectionModel().setSelectionMode(MULTIPLE_INTERVAL_SELECTION);

    JBScrollPane scrollPane = new JBScrollPane(myDependencyTable);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    add(scrollPane, BorderLayout.CENTER);

    myDependencyTable.updateColumnSizes();
  }
}