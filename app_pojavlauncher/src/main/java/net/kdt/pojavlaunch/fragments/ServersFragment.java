package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.servers.QuickPlay;
import net.kdt.pojavlaunch.servers.SavedServer;
import net.kdt.pojavlaunch.servers.ServerPinger;
import net.kdt.pojavlaunch.servers.ServerStore;

import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.R;

/** Saved and recently played servers, with live status and one-tap join. */
public class ServersFragment extends Fragment {
    public static final String TAG = "ServersFragment";

    private final List<SavedServer> mServers = new ArrayList<>();
    private ServerAdapter mAdapter;
    private TextView mStatus;
    private int mGeneration;

    public ServersFragment() {
        super(R.layout.fragment_servers);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mStatus = view.findViewById(R.id.servers_status);
        RecyclerView list = view.findViewById(R.id.servers_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        mAdapter = new ServerAdapter();
        list.setAdapter(mAdapter);
        view.findViewById(R.id.servers_refresh).setOnClickListener(v -> reload());
        view.findViewById(R.id.servers_add).setOnClickListener(v -> showAddDialog());

        Instance instance = Instances.loadSelectedInstance();
        ((TextView) view.findViewById(R.id.servers_instance)).setText(instance == null
                ? getString(R.string.no_instance)
                : getString(R.string.servers_join_with, instance.name));
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        final int generation = ++mGeneration;
        PojavApplication.sExecutorService.execute(() -> {
            List<SavedServer> servers = ServerStore.loadAll();
            Tools.runOnUiThread(() -> {
                if(!isAdded() || getView() == null || generation != mGeneration) return;
                mServers.clear();
                mServers.addAll(servers);
                mAdapter.notifyDataSetChanged();
                mStatus.setVisibility(servers.isEmpty() ? View.VISIBLE : View.GONE);
                for(SavedServer server : servers) ping(server, generation);
            });
        });
    }

    private void ping(SavedServer server, int generation) {
        server.pinging = true;
        server.pingFailed = false;
        PojavApplication.sExecutorService.execute(() -> {
            ServerPinger.Status status = null;
            try {
                status = ServerPinger.ping(server.address);
            }catch (Exception ignored) {}
            final ServerPinger.Status result = status;
            Tools.runOnUiThread(() -> {
                if(!isAdded() || generation != mGeneration) return;
                server.pinging = false;
                server.status = result;
                server.pingFailed = result == null;
                int index = mServers.indexOf(server);
                if(index >= 0) mAdapter.notifyItemChanged(index);
            });
        });
    }

    private void play(SavedServer server) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        QuickPlay.request(server.address);
        PojavApplication.sExecutorService.execute(() -> ServerStore.markPlayed(server, instance.name));
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void showAddDialog() {
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_server, null);
        EditText name = content.findViewById(R.id.add_server_name);
        EditText address = content.findViewById(R.id.add_server_address);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.servers_add)
                .setView(content)
                .setPositiveButton(R.string.servers_add, (d, w) -> {
                    String serverAddress = address.getText().toString().trim();
                    if(serverAddress.isEmpty()) return;
                    String serverName = name.getText().toString().trim();
                    String finalName = serverName.isEmpty() ? serverAddress : serverName;
                    PojavApplication.sExecutorService.execute(() -> {
                        ServerStore.add(finalName, serverAddress);
                        Tools.runOnUiThread(this::reload);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmRemove(SavedServer server) {
        new AlertDialog.Builder(requireContext())
                .setMessage(getString(R.string.servers_remove_confirm, server.name))
                .setPositiveButton(R.string.content_remove, (d, w) -> PojavApplication.sExecutorService.execute(() -> {
                    ServerStore.remove(server);
                    Tools.runOnUiThread(this::reload);
                }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String describe(SavedServer server) {
        StringBuilder meta = new StringBuilder();
        if(server.status != null) {
            meta.append(getString(R.string.servers_players, server.status.online, server.status.max));
            meta.append(" · ").append(getString(R.string.servers_ping, server.status.latency));
            if(!server.status.version.isEmpty()) meta.append(" · ").append(server.status.version);
        }
        if(server.lastPlayed > 0) {
            if(meta.length() > 0) meta.append(" · ");
            meta.append(DateUtils.getRelativeTimeSpanString(server.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        } else if(server.instanceName != null) {
            if(meta.length() > 0) meta.append(" · ");
            meta.append(server.instanceName);
        }
        return meta.toString();
    }

    private class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView motd;
            final TextView meta;
            final Button play;

            Holder(View view) {
                super(view);
                icon = view.findViewById(R.id.server_icon);
                name = view.findViewById(R.id.server_name);
                motd = view.findViewById(R.id.server_motd);
                meta = view.findViewById(R.id.server_meta);
                play = view.findViewById(R.id.server_play);
                icon.setClipToOutline(true);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_server, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            SavedServer server = mServers.get(position);
            holder.name.setText(server.name);
            if(server.status != null && server.status.icon != null) {
                holder.icon.setPadding(0, 0, 0, 0);
                holder.icon.setImageBitmap(server.status.icon);
            } else {
                int padding = (int) (12 * holder.icon.getResources().getDisplayMetrics().density);
                holder.icon.setPadding(padding, padding, padding, padding);
                holder.icon.setImageResource(R.drawable.ic_nav_servers);
            }
            if(server.pinging) {
                holder.motd.setText(R.string.servers_pinging);
                holder.motd.setTextColor(ContextCompat.getColor(holder.motd.getContext(), R.color.text_tertiary));
            } else if(server.pingFailed) {
                holder.motd.setText(R.string.servers_offline);
                holder.motd.setTextColor(0xFFFF6B6B);
            } else if(server.status != null) {
                holder.motd.setText(server.status.motd.isEmpty() ? server.address : server.status.motd);
                holder.motd.setTextColor(ContextCompat.getColor(holder.motd.getContext(), R.color.text_default));
            } else {
                holder.motd.setText(server.address);
            }
            holder.meta.setText(describe(server));
            holder.play.setOnClickListener(v -> play(server));
            holder.itemView.setOnLongClickListener(v -> {
                confirmRemove(server);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mServers.size();
        }
    }
}
