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
package com.android.tools.profilers.network;

import com.android.tools.adtui.Choreographer;
import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.RangedTable;
import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.chart.StateChart;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.formatter.TimeAxisFormatter;
import com.android.tools.adtui.model.DefaultDataSeries;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.RangedTableModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.ProfilerColors.*;

/**
 * This class responsible for displaying table of connections information (e.g url, duration, timeline)
 * for network profiling. Each row in the table represents a single connection.
 */
final class ConnectionsView {
  private static final int ROW_HEIGHT_PADDING = 5;

  private Range mySelectionRange;

  public interface DetailedViewListener {
    void showDetailedConnection(HttpData data);
  }

  private enum NetworkState {
    SENDING, RECEIVING, WAITING, NONE
  }

  private static final EnumMap<NetworkState, Color> NETWORK_STATE_COLORS = new EnumMap<>(NetworkState.class);

  static {
    NETWORK_STATE_COLORS.put(NetworkState.SENDING, NETWORK_SENDING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.RECEIVING, NETWORK_RECEIVING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.WAITING, NETWORK_WAITING_COLOR);
    NETWORK_STATE_COLORS.put(NetworkState.NONE, AdtUiUtils.DEFAULT_BACKGROUND_COLOR);
  }

  /**
   * Columns for each connection information
   */
  @VisibleForTesting
  enum Column {
    URL(0.25, String.class),
    SIZE(0.25/4, Integer.class),
    TYPE(0.25/4, String.class),
    STATUS(0.25/4, Integer.class),
    TIME(0.25/4, Long.class),
    TIMELINE(0.5, Long.class);

    private final double myWidthPercentage;
    private final Class<?> myType;

    Column(double widthPercentage, Class<?> type) {
      myWidthPercentage = widthPercentage;
      myType = type;
    }

    public double getWidthPercentage() {
      return myWidthPercentage;
    }

    public Class<?> getType() {
      return myType;
    }

    public String toDisplayString() {
      return StringUtil.capitalize(name().toLowerCase(Locale.getDefault()));
    }

    public Object getValueFrom(HttpData data) {
      switch (this) {
        case URL:
          return data.getUrl();

        case SIZE:
          String contentLength = data.getResponseField(HttpData.FIELD_CONTENT_LENGTH);
          return (contentLength != null) ? Integer.parseInt(contentLength) : -1;

        case TYPE:
          String contentType = data.getResponseField(HttpData.FIELD_CONTENT_TYPE);
          return StringUtil.notNullize(contentType);

        case STATUS:
          return data.getStatusCode();

        case TIME:
          return data.getEndTimeUs() - data.getStartTimeUs();

        case TIMELINE:
          return data.getStartTimeUs();

        default:
          throw new UnsupportedOperationException("getValueFrom not implemented for: " + this);
      }
    }
  }

  @NotNull
  private final DetailedViewListener myDetailedViewListener;

  @NotNull
  private final NetworkProfilerStage myStage;

  @NotNull
  private final ConnectionsTableModel myTableModel;

  @NotNull
  private final JTable myConnectionsTable;

  @NotNull
  private final Choreographer myChoreographer;

  public ConnectionsView(@NotNull NetworkProfilerStageView stageView,
                         @NotNull DetailedViewListener detailedViewListener) {
    this(stageView.getStage(), stageView.getChoreographer(), stageView.getTimeline().getSelectionRange(), detailedViewListener);
  }

  @VisibleForTesting
  public ConnectionsView(@NotNull NetworkProfilerStage stage,
                         @NotNull Choreographer choreographer,
                         @NotNull Range selectionRange,
                         @NotNull DetailedViewListener detailedViewListener) {
    myStage = stage;
    myDetailedViewListener = detailedViewListener;
    myTableModel = new ConnectionsTableModel();
    mySelectionRange = selectionRange;
    myChoreographer = choreographer;
    RangedTable rangedTable = new RangedTable(mySelectionRange, myTableModel);
    choreographer.register(rangedTable);
    myConnectionsTable = createRequestsTable();
  }

  @NotNull
  public JComponent getComponent() {
    return myConnectionsTable;
  }

  @NotNull
  private JTable createRequestsTable() {
    JTable table = new ConnectionsTable(myTableModel);
    table.setAutoCreateRowSorter(true);
    table.getColumnModel().getColumn(Column.SIZE.ordinal()).setCellRenderer(new SizeRenderer());
    table.getColumnModel().getColumn(Column.STATUS.ordinal()).setCellRenderer(new StatusRenderer());
    table.getColumnModel().getColumn(Column.TIME.ordinal()).setCellRenderer(new TimeRenderer());
    table.getColumnModel().getColumn(Column.TIMELINE.ordinal()).setCellRenderer(
      new TimelineRenderer(table, mySelectionRange));

    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getSelectionModel().addListSelectionListener(e -> {
      int selectedRow = table.getSelectedRow();
      if (0 <= selectedRow && selectedRow < myTableModel.getRowCount()) {
        int modelRow = myConnectionsTable.convertRowIndexToModel(selectedRow);
        myDetailedViewListener.showDetailedConnection(myTableModel.getHttpData(modelRow));
      }
    });

    table.setFont(AdtUiUtils.DEFAULT_FONT);
    table.setShowVerticalLines(true);
    table.setShowHorizontalLines(false);
    int defaultFontHeight = table.getFontMetrics(AdtUiUtils.DEFAULT_FONT).getHeight();
    table.setRowHeight(defaultFontHeight + ROW_HEIGHT_PADDING);

    table.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        for (int i = 0; i < Column.values().length; ++i) {
          Column column = Column.values()[i];
          table.getColumnModel().getColumn(i).setPreferredWidth((int)(table.getWidth() * column.getWidthPercentage()));
        }
      }
    });

    // Keep the previously selected row selected if it's still there

    // IJ wants to use Application.invokeLater, but we use SwingUtilities.invokeLater because it's
    // testable (Application is null in basic unit tests)
    //noinspection SSBasedInspection
    myTableModel.addTableModelListener(e ->
      SwingUtilities.invokeLater(() -> {
        // Invoke later, because table itself listener of table model.
        HttpData selectedData = myStage.getConnection();
        if (selectedData != null) {
          for (int i = 0; i < myTableModel.getRowCount(); ++i) {
            if (myTableModel.getHttpData(i).getId() == selectedData.getId()) {
              int row = table.convertRowIndexToView(i);
              table.setRowSelectionInterval(row, row);
              break;
            }
          }
        }
      })
    );

    return table;
  }

  private final class ConnectionsTableModel extends AbstractTableModel implements RangedTableModel {
    @NotNull private List<HttpData> myDataList = new ArrayList<>();
    @NotNull private final Range myLastRange = new Range();

    @Override
    public int getRowCount() {
      return myDataList.size();
    }

    @Override
    public int getColumnCount() {
      return Column.values().length;
    }

    @Override
    public String getColumnName(int column) {
      return Column.values()[column].toDisplayString();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      HttpData data = myDataList.get(rowIndex);
      return Column.values()[columnIndex].getValueFrom(data);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return Column.values()[columnIndex].getType();
    }

    @NotNull
    public HttpData getHttpData(int rowIndex) {
      return myDataList.get(rowIndex);
    }

    @Override
    public void update(@NotNull Range range) {
      if (myLastRange.getMin() != range.getMin() || myLastRange.getMax() != range.getMax()) {
        myDataList = myStage.getRequestsModel().getData(range);
        fireTableDataChanged();
        myLastRange.set(range);
      }
    }
  }

  private static final class SizeRenderer extends DefaultTableCellRenderer {
    @Override
    protected void setValue(Object value) {
      int bytes = (Integer)value;
      setText(bytes >= 0 ? StringUtil.formatFileSize(bytes) : "");
    }
  }

  private static final class StatusRenderer extends DefaultTableCellRenderer {
    @Override
    protected void setValue(Object value) {
      Integer status = (Integer)value;
      setText(status > -1 ? Integer.toString(status) : "");
    }
  }

  private static final class TimeRenderer extends DefaultTableCellRenderer {
    @Override
    protected void setValue(Object value) {
      Long durationUs = (Long)value;
      if (durationUs >= 0) {
        long durationMs = TimeUnit.MICROSECONDS.toMillis(durationUs);
        setText(StringUtil.formatDuration(durationMs));
      }
      else {
        setText("");
      }
    }
  }

  private final class TimelineRenderer implements TableCellRenderer, TableModelListener {
    private final JBColor AXIS_COLOR = new JBColor(Gray._103, Gray._120);
    /**
     * Keep in sync 1:1 with {@link ConnectionsTableModel#myDataList}. When the table asks for the
     * chart to render, it will be converted from model index to view index.
     */
    @NotNull private final List<StateChart<NetworkState>> myCharts;
    @NotNull private final JTable myTable;
    @NotNull private final AxisComponent myAxis;
    private final Range myRange;

    TimelineRenderer(@NotNull JTable table, @NotNull Range range) {
      myRange = range;
      myCharts = new ArrayList<>();
      myTable = table;
      myAxis = buildAxis();
      myTable.getModel().addTableModelListener(this);
      myChoreographer.register(myAxis);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      JComponent chart = myCharts.get(myTable.convertRowIndexToModel(row));
      if (row == 0) {
        JPanel panel = new JBPanel(new TabularLayout("*" , "*"));
        panel.add(myAxis, new TabularLayout.Constraint(0, 0));
        panel.add(chart, new TabularLayout.Constraint(0, 0));
        return panel;
      }

      return chart;
    }

    @Override
    public void tableChanged(TableModelEvent e) {
      myCharts.clear();
      ConnectionsTableModel model = (ConnectionsTableModel)myTable.getModel();
      for (int i = 0; i < model.getRowCount(); ++i) {
        HttpData data = model.getHttpData(i);
        DefaultDataSeries<NetworkState> series = new DefaultDataSeries<>();
        series.add(0, NetworkState.NONE);
        series.add(data.getStartTimeUs(), NetworkState.SENDING);
        if (data.getDownloadingTimeUs() > 0) {
          series.add(data.getDownloadingTimeUs(), NetworkState.RECEIVING);
        }
        if (data.getEndTimeUs() > 0) {
          series.add(data.getEndTimeUs(), NetworkState.NONE);
        }

        StateChart<NetworkState> chart = new StateChart<>(NETWORK_STATE_COLORS);
        chart.addSeries(new RangedSeries<>(myRange, series));
        chart.animate(1);
        myCharts.add(chart);
      }
    }

    @NotNull
    private AxisComponent buildAxis() {
      AxisComponent.Builder builder = new AxisComponent.Builder(myRange, new TimeAxisFormatter(1, 4, 1),
                                                                AxisComponent.AxisOrientation.BOTTOM);
      builder.setGlobalRange(myStage.getStudioProfilers().getDataRange()).showAxisLine(false)
        .setOffset(myStage.getStudioProfilers().getDeviceStartUs());
      AxisComponent axis = builder.build();
      axis.setForeground(AXIS_COLOR);
      return axis;
    }
  }

  private static final class ConnectionsTable extends JBTable {
    private static final JBColor HOVER_COLOR = new JBColor(0xEAEFFA, 0xEAEFFA);
    private int myHoveredRow = -1;

    ConnectionsTable(@NotNull TableModel model) {
      super(model);
      MouseAdapter mouseAdapter = new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          myHoveredRow = rowAtPoint(e.getPoint());
        }

        @Override
        public void mouseExited(MouseEvent e) {
          myHoveredRow = -1;
        }
      };
      addMouseMotionListener(mouseAdapter);
      addMouseListener(mouseAdapter);
    }

    @NotNull
    @Override
    public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
      Component comp = super.prepareRenderer(renderer, row, column);
      if (isRowSelected(row)) {
        comp.setForeground(getSelectionForeground());
        comp.setBackground(getSelectionBackground());
      }
      else if (row == myHoveredRow) {
        comp.setBackground(HOVER_COLOR);
        comp.setForeground(getForeground());
      }
      else {
        comp.setBackground(getBackground());
        comp.setForeground(getForeground());
      }
      return comp;
    }
  }
}
