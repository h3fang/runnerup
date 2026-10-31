/*
 * Copyright (C) 2025 robert.jonsson75@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.view;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.google.android.material.tabs.TabLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.runnerup.R;
import org.runnerup.common.util.Constants;
import org.runnerup.db.DBHelper;
import org.runnerup.util.Formatter;
import org.runnerup.workout.Sport;

public class StatisticsFragment extends Fragment implements Constants, LoaderCallbacks<Cursor> {

  private static final String ARG_MONTHLY = "monthly";

  private SQLiteDatabase mDB = null;
  private Formatter formatter = null;

  private StatisticsListAdapter cursorAdapter = null;
  private BarChart chart = null;
  private TextView emptyView = null;

  private int sportFilter = -1;
  private boolean monthly = true;

  public StatisticsFragment() {
    super(R.layout.statistics);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    Context context = requireContext();
    mDB = DBHelper.getReadableDatabase(context);
    formatter = new Formatter(context);

    ListView listView = view.findViewById(R.id.statistics_list);
    listView.setDividerHeight(2);
    cursorAdapter = new StatisticsListAdapter(context, null);
    listView.setAdapter(cursorAdapter);

    emptyView = view.findViewById(R.id.statistics_empty);

    chart = new BarChart(context);
    chart.getDescription().setEnabled(false);
    chart.getLegend().setEnabled(false);
    chart.setDrawGridBackgroundEnabled(false);
    chart.setScaleEnabled(false);
    chart.setPinchZoomEnabled(false);
    chart.setDragEnabled(true);

    int textColor = ContextCompat.getColor(context, R.color.colorText);
    int gridColor = ColorUtils.setAlphaComponent(textColor, 64);
    chart.setNoDataText("");
    chart.setNoDataTextColor(textColor);
    chart.getAxisLeft().setAxisMinimum(0f);
    chart.getAxisLeft().setTextColor(textColor);
    chart.getAxisLeft().setAxisLineColor(textColor);
    chart.getAxisLeft().setGridColor(gridColor);
    chart.getAxisLeft().setTextSize(12f);
    chart.getAxisLeft().setAxisLineWidth(1f);
    chart
        .getAxisLeft()
        .setValueFormatter(
            (value, axis) ->
                formatter.formatDistance(Formatter.Format.TXT, (long) value)
                    + " "
                    + formatter.getDistanceUnit());
    chart.getAxisRight().setEnabled(false);

    chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
    chart.getXAxis().setGranularity(1f);
    chart.getXAxis().setDrawGridLinesEnabled(false);
    chart.getXAxis().setLabelRotationAngle(-45f);
    chart.getXAxis().setTextColor(textColor);
    chart.getXAxis().setAxisLineColor(textColor);
    chart.getXAxis().setTextSize(12f);
    chart.getXAxis().setAxisLineWidth(1f);
    ViewGroup graphContainer = view.findViewById(R.id.statistics_graph);
    graphContainer.addView(
        chart,
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    Spinner sportSpinner = view.findViewById(R.id.statistics_sport_spinner);
    List<String> entries = new ArrayList<>();
    entries.add(getString(org.runnerup.common.R.string.All));
    for (String s : Sport.getStringArray(getResources())) {
      entries.add(s);
    }
    ArrayAdapter<String> sportAdapter =
        new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, entries);
    sportAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sportSpinner.setAdapter(sportAdapter);
    sportSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
            sportFilter = position - 1;
            reload();
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });

    TabLayout periodTabs = view.findViewById(R.id.statistics_period_tabs);
    periodTabs.addTab(periodTabs.newTab().setText(org.runnerup.common.R.string.Monthly), true);
    periodTabs.addTab(periodTabs.newTab().setText(org.runnerup.common.R.string.Yearly));
    periodTabs.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            monthly = tab.getPosition() == 0;
            reload();
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {}
        });

    LoaderManager.getInstance(this).initLoader(0, loaderArgs(), this);
  }

  private Bundle loaderArgs() {
    Bundle args = new Bundle();
    args.putBoolean(ARG_MONTHLY, monthly);
    return args;
  }

  private void reload() {
    if (mDB != null) {
      LoaderManager.getInstance(this).restartLoader(0, loaderArgs(), this);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    reload();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DBHelper.closeDB(mDB);
    mDB = null;
  }

  @NonNull
  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    boolean isMonthly = args == null || args.getBoolean(ARG_MONTHLY, true);
    String periodFormat = isMonthly ? "%Y-%m" : "%Y";

    StringBuilder sql =
        new StringBuilder()
            .append("SELECT MIN(")
            .append(DB.PRIMARY_KEY)
            .append(") AS ")
            .append(DB.PRIMARY_KEY)
            .append(", strftime('")
            .append(periodFormat)
            .append("', ")
            .append(DB.ACTIVITY.START_TIME)
            .append(", 'unixepoch', 'localtime') AS period")
            .append(", SUM(")
            .append(DB.ACTIVITY.DISTANCE)
            .append(") AS distance")
            .append(", SUM(")
            .append(DB.ACTIVITY.TIME)
            .append(") AS duration")
            .append(", COUNT(*) AS cnt")
            .append(" FROM ")
            .append(DB.ACTIVITY.TABLE)
            .append(" WHERE ")
            .append(DB.ACTIVITY.DELETED)
            .append(" = 0");

    String[] selectionArgs = null;
    if (sportFilter >= 0) {
      sql.append(" AND ").append(DB.ACTIVITY.SPORT).append(" = ?");
      selectionArgs = new String[] {Integer.toString(sportFilter)};
    }
    sql.append(" GROUP BY period ORDER BY period DESC");

    return new StatisticsCursorLoader(requireContext(), mDB, sql.toString(), selectionArgs);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
    cursorAdapter.swapCursor(cursor);
    updateGraph(cursor);
    emptyView.setVisibility(cursor == null || cursor.getCount() == 0 ? View.VISIBLE : View.GONE);
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    cursorAdapter.swapCursor(null);
    updateGraph(null);
  }

  private void updateGraph(Cursor cursor) {
    chart.clear();
    if (cursor == null || cursor.getCount() == 0) {
      chart.invalidate();
      return;
    }

    int distanceCol = cursor.getColumnIndexOrThrow("distance");
    int n = cursor.getCount();
    List<BarEntry<Object>> bars = new ArrayList<>();
    List<String> labels = new ArrayList<>();
    // The cursor is ordered newest first; draw the chart oldest to newest.
    for (int i = 0; i < n; i++) {
      cursor.moveToPosition(n - 1 - i);
      bars.add(new BarEntry<Object>(i, (float) cursor.getDouble(distanceCol), null, null));
      labels.add(formatPeriodLabel(cursor.getString(cursor.getColumnIndexOrThrow("period")), true));
    }
    cursor.moveToFirst();

    BarDataSet<Object> dataSet = new BarDataSet<>(bars, "");
    dataSet.setColor(ContextCompat.getColor(requireContext(), R.color.colorAccent));
    dataSet.setDrawValuesEnabled(false);

    BarData barData = new BarData(dataSet);
    barData.setBarWidth(0.7f);
    chart.setData(barData);

    chart
        .getXAxis()
        .setValueFormatter(
            (value, axis) -> {
              int index = Math.round(value);
              return index >= 0 && index < labels.size() ? labels.get(index) : "";
            });

    chart.setVisibleXRangeMaximum(12f);
    chart.moveViewToX(0f);
    chart.invalidate();
  }

  private String formatPeriodLabel(String period, boolean shortLabel) {
    if (period == null) {
      return "";
    }
    if (!monthly) {
      return period;
    }
    try {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(Calendar.YEAR, Integer.parseInt(period.substring(0, 4)));
      cal.set(Calendar.MONTH, Integer.parseInt(period.substring(5, 7)) - 1);
      if (shortLabel) {
        return new SimpleDateFormat("LLL yy", Locale.getDefault()).format(cal.getTime());
      }
      return formatter.formatMonth(cal.getTime());
    } catch (NumberFormatException | IndexOutOfBoundsException e) {
      return period;
    }
  }

  private static class StatisticsCursorLoader extends CursorLoader {
    private final SQLiteDatabase mDB;
    private final String mSql;
    private final String[] mArgs;
    private final ForceLoadContentObserver mObserver = new ForceLoadContentObserver();

    StatisticsCursorLoader(Context context, SQLiteDatabase db, String sql, String[] args) {
      super(context);
      mDB = db;
      mSql = sql;
      mArgs = args;
    }

    @Override
    public Cursor loadInBackground() {
      Cursor cursor;
      try {
        cursor = mDB.rawQuery(mSql, mArgs);
      } catch (IllegalStateException e) {
        // The database can be closed while a database import is in progress
        Log.e(getClass().getName(), "Statistics query failed:", e);
        return null;
      }
      if (cursor != null) {
        // Ensure the cursor window is filled
        cursor.getCount();
        cursor.registerContentObserver(mObserver);
      }
      return cursor;
    }
  }

  class StatisticsListAdapter extends CursorAdapter {
    final LayoutInflater inflater;

    StatisticsListAdapter(Context context, Cursor c) {
      super(context, c, true);
      inflater = LayoutInflater.from(context);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      String period = cursor.getString(cursor.getColumnIndexOrThrow("period"));
      long distance = cursor.getLong(cursor.getColumnIndexOrThrow("distance"));
      long duration = cursor.getLong(cursor.getColumnIndexOrThrow("duration"));
      int count = cursor.getInt(cursor.getColumnIndexOrThrow("cnt"));

      TextView periodText = view.findViewById(R.id.statistics_row_period);
      periodText.setText(formatPeriodLabel(period, false));

      TextView distanceText = view.findViewById(R.id.statistics_row_distance);
      distanceText.setText(formatter.formatDistance(Formatter.Format.TXT_SHORT, distance));

      TextView durationText = view.findViewById(R.id.statistics_row_duration);
      durationText.setText(formatter.formatElapsedTime(Formatter.Format.TXT_SHORT, duration));

      TextView countText = view.findViewById(R.id.statistics_row_count);
      countText.setText(Integer.toString(count));

      TextView paceText = view.findViewById(R.id.statistics_row_pace);
      if (distance > 0 && duration > 0) {
        paceText.setText(
            formatter.formatVelocityByPreferredUnit(
                Formatter.Format.TXT_LONG, (double) distance / duration));
      } else {
        paceText.setText("");
      }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      return inflater.inflate(R.layout.statistics_row, parent, false);
    }
  }
}
