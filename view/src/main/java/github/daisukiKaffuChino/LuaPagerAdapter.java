package github.daisukiKaffuChino;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * v2 迁移：基于 {@link RecyclerView.Adapter} 的 ViewPager2 适配器。
 * 保留与 v1 相同的 Lua 侧方法签名（add/insert/remove/getItem/getData）。
 */
@Keep
public class LuaPagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<View> pagerViews;

    public LuaPagerAdapter(List<View> list) {
        this.pagerViews = list;
    }

    public LuaPagerAdapter(List<View> list, List<String> titles) {
        this.pagerViews = list;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = pagerViews.get(viewType);
        return new RecyclerView.ViewHolder(v) {};
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        // 页面视图已在 onCreateViewHolder 直接挂载，无需额外绑定
    }

    @Override
    public int getItemCount() {
        return this.pagerViews.size();
    }

    public void add(View view) {
        pagerViews.add(view);
        notifyItemInserted(pagerViews.size() - 1);
    }

    public void insert(int index, View view) {
        pagerViews.add(index, view);
        notifyItemInserted(index);
    }

    public View remove(int index) {
        View v = pagerViews.remove(index);
        notifyItemRemoved(index);
        return v;
    }

    public boolean remove(View view) {
        int index = pagerViews.indexOf(view);
        boolean removed = pagerViews.remove(view);
        if (removed) {
            notifyItemRemoved(index);
        }
        return removed;
    }

    public View getItem(int index) {
        return pagerViews.get(index);
    }

    public List<View> getData() {
        return pagerViews;
    }
}