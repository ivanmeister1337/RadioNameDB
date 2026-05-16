package com.belgee.radionamedb;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class StationsAdapter extends RecyclerView.Adapter<StationsAdapter.VH> {

    public interface Listener {
        void onEdit(Station s);
        void onDelete(Station s);
    }

    private List<Station> stations;
    private final Listener listener;

    public StationsAdapter(List<Station> initial, Listener listener) {
        this.stations = initial;
        this.listener = listener;
    }

    public void setStations(List<Station> list) {
        this.stations = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_station, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Station s = stations.get(pos);
        h.freqText.setText(s.displayFreq());
        h.nameText.setText(s.name);
        h.itemView.setOnClickListener(v -> listener.onEdit(s));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(s));
    }

    @Override
    public int getItemCount() { return stations.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView freqText, nameText;
        ImageButton btnDelete;
        VH(View v) {
            super(v);
            freqText = v.findViewById(R.id.freqText);
            nameText = v.findViewById(R.id.nameText);
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}
