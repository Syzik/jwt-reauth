/*
Copyright 2022 NCC Group
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    https://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.nccgroup.jwtreauth.ui.scope;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.table.TableRowSorter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/*
    Note: In this class I use Active column and "in scope" column interchangeably.
          They both refer to the first column in the table.
 */
public class ScopeTable extends JTable {
    private final TableRowSorter<ScopeTableModel> sorter;

    private final InScopeFilter scopeFilter;
    private String filterSearch;

    private final JComboBox<String> profileComboBox;

    ScopeTable() {
        setModel(new ScopeTableModel());

        // fix the size of the "active" / "in scope" column
        final var columnModel = getColumnModel();
        columnModel.getColumn(ScopeTableModel.IN_SCOPE_COL).setPreferredWidth(50);
        columnModel.getColumn(ScopeTableModel.IN_SCOPE_COL).setMaxWidth(50);
        columnModel.getColumn(ScopeTableModel.IS_PREFIX_COL).setPreferredWidth(50);
        columnModel.getColumn(ScopeTableModel.IS_PREFIX_COL).setMaxWidth(50);
        columnModel.getColumn(ScopeTableModel.PROFILE_COL).setPreferredWidth(120);
        columnModel.getColumn(ScopeTableModel.PROFILE_COL).setMaxWidth(200);

        // set up the profile combo box editor
        profileComboBox = new JComboBox<>();
        columnModel.getColumn(ScopeTableModel.PROFILE_COL).setCellEditor(new DefaultCellEditor(profileComboBox));

        sorter = new TableRowSorter<>((ScopeTableModel) getModel());
        sorter.setSortsOnUpdates(true);
        setRowSorter(sorter);

        setFillsViewportHeight(true);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        addPopupMenu();
        addEmptyRow();

        scopeFilter = new InScopeFilter();
        filterSearch = "";

        // create the filter
        updateFilter();
    }

    private void addPopupMenu() {
        var removeItem = new JMenuItem("Remove");

        removeItem.addActionListener(e -> {
            // Convert view indices to model indices, then sort biggest to smallest
            // so removing earlier rows doesn't shift later ones
            var modelRows = Arrays.stream(getSelectedRows())
                    .map(this::convertRowIndexToModel)
                    .boxed()
                    .toArray(Integer[]::new);
            Arrays.sort(modelRows, Collections.reverseOrder());

            for (var row : modelRows) {
                ((ScopeTableModel) getModel()).removeRow(row);
            }

            ensureEmptyLastRow();
            adjustColumnWidths();
        });

        var popupMenu = new JPopupMenu();
        popupMenu.add(removeItem);

        setComponentPopupMenu(popupMenu);
    }

    /*
     * Override the base editingStopped method so that we can check whether the cell that
     * was just edited, was:
     * a. the last cell in the URL column
     * b. filled with a non-blank value
     *
     * We do this by getting the editing row and column, then letting the editor finish up,
     * and finally we can check the value in the cell. Adding a new empty row if a non-empty value was entered.
     *
     * This ordering of events is important for the following reasons:
     * 1. The editing row and column are both -1 once editingStopped(), is called.
     * 2. The cell's value will be blank before we call editingStopped().
     */
    @Override
    public void editingStopped(ChangeEvent e) {
        // NOTE: ngl chief this is almost certainly the wrong way to do this, but it works.

        // grab the row and column before they become invalid
        var row = this.getEditingRow();
        var col = this.getEditingColumn();

        // allow the editor to finish up
        super.editingStopped(e);

        // if we edited the URL and it was the last row
        if (col == ScopeTableModel.URL_COL && row == getModel().getRowCount() - 1) {
            // check the contents of the edited cell to see if they are blank
            var cellUrl = (String) getModel().getValueAt(row, ScopeTableModel.URL_COL);

            if (!cellUrl.isBlank()) {
                // if the new URL is not blank then add a new empty row
                addEmptyRow();
            }
        }

        adjustColumnWidths();
    }

    /**
     * helper method to ensure that the last row in the scope table is blank
     * so the user can add a scope to it by typing
     */
    private synchronized void ensureEmptyLastRow() {
        var rows = getModel().getRowCount();

        if (rows == 0) {
            addEmptyRow();
        } else {
            var lastUrl = (String) getModel().getValueAt(rows - 1, ScopeTableModel.URL_COL);
            if (!lastUrl.isBlank()) {
                addEmptyRow();
            }
        }
    }

    synchronized void addEmptyRow() {
        ((ScopeTableModel) getModel()).addRow(false, false, "", "");
    }

    synchronized void addRow(String url, String profileName) {
        var model = (ScopeTableModel) getModel();
        var rows = model.getRowCount();

        if (rows == 0) {
            model.addRow(true, false, url, profileName);
        } else {
            var lastUrl = (String) model.getValueAt(rows - 1, ScopeTableModel.URL_COL);

            if (lastUrl.isBlank()) {
                model.setRow(rows - 1, true, true, url, profileName);
            } else {
                model.addRow(true, true, url, profileName);
            }
        }

        // always keep an empty row free at the end
        addEmptyRow();
        adjustColumnWidths();
    }

    private void adjustColumnWidths() {
        SwingUtilities.invokeLater(() -> {
            var fm = getFontMetrics(getFont());
            int maxUrlWidth = fm.stringWidth("URL") + 20;

            for (int row = 0; row < getRowCount(); row++) {
                var val = getModel().getValueAt(convertRowIndexToModel(row), ScopeTableModel.URL_COL);
                if (val != null) {
                    int width = fm.stringWidth(val.toString()) + 20;
                    if (width > maxUrlWidth) maxUrlWidth = width;
                }
            }

            var urlCol = getColumnModel().getColumn(ScopeTableModel.URL_COL);
            urlCol.setPreferredWidth(maxUrlWidth);

            // Profile column fits its content too
            int maxProfileWidth = fm.stringWidth("Profile") + 20;
            for (int row = 0; row < getRowCount(); row++) {
                var val = getModel().getValueAt(convertRowIndexToModel(row), ScopeTableModel.PROFILE_COL);
                if (val != null) {
                    int width = fm.stringWidth(val.toString()) + 20;
                    if (width > maxProfileWidth) maxProfileWidth = width;
                }
            }

            var profileCol = getColumnModel().getColumn(ScopeTableModel.PROFILE_COL);
            profileCol.setPreferredWidth(maxProfileWidth);
            profileCol.setMaxWidth(Integer.MAX_VALUE);
        });
    }

    void updateProfileComboBox(List<String> profileNames) {
        profileComboBox.removeAllItems();
        profileComboBox.addItem("");
        for (String name : profileNames) {
            profileComboBox.addItem(name);
        }
    }

    void setFilterSearch(String filterSearch) {
        this.filterSearch = filterSearch;

        updateFilter();
    }

    void setFilterScope(ScopeFilter filter) {
        this.scopeFilter.setFilter(filter);

        this.sorter.sort();
    }

    private void updateFilter() {
        SwingUtilities.invokeLater(() -> sorter.setRowFilter(
                RowFilter.andFilter(List.of(
                        scopeFilter,
                        RowFilter.regexFilter(this.filterSearch, ScopeTableModel.URL_COL)
                ))
        ));
    }
}
