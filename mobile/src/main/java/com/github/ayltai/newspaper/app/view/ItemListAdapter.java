package com.github.ayltai.newspaper.app.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import com.github.ayltai.newspaper.app.data.model.FeaturedItem;
import com.github.ayltai.newspaper.app.widget.ContentView;
import com.github.ayltai.newspaper.app.widget.FeaturedView;
import com.github.ayltai.newspaper.app.widget.FooterView;
import com.github.ayltai.newspaper.app.widget.HeaderView;
import com.github.ayltai.newspaper.app.widget.ImageView;
import com.github.ayltai.newspaper.app.widget.MetaView;
import com.github.ayltai.newspaper.data.DaggerDataComponent;
import com.github.ayltai.newspaper.data.DataManager;
import com.github.ayltai.newspaper.data.DataModule;
import com.github.ayltai.newspaper.data.ItemManager;
import com.github.ayltai.newspaper.data.model.Item;
import com.github.ayltai.newspaper.data.model.NewsItem;
import com.github.ayltai.newspaper.util.Animations;
import com.github.ayltai.newspaper.util.RxUtils;
import com.github.ayltai.newspaper.util.StringUtils;
import com.github.ayltai.newspaper.util.TestUtils;
import com.github.ayltai.newspaper.view.SimpleUniversalAdapter;
import com.github.ayltai.newspaper.view.binding.BinderFactory;
import com.github.ayltai.newspaper.view.binding.FullBinderFactory;
import com.github.ayltai.newspaper.widget.DelegatingFilter;
import com.github.ayltai.newspaper.widget.SimpleViewHolder;

import io.reactivex.Single;
import io.realm.Realm;

public final class ItemListAdapter extends SimpleUniversalAdapter<Item, View, SimpleViewHolder<View>> implements Filterable {
    public static final class Builder {
        private final Collection<BinderFactory<Item>> factories = new ArrayList<>();
        private final Context                         context;

        public Builder(@NonNull final Context context) {
            this.context = context;
        }

        @NonNull
        public ItemListAdapter.Builder addBinderFactory(@NonNull final BinderFactory<Item> factory) {
            this.factories.add(factory);

            return this;
        }

        @NonNull
        public ItemListAdapter build() {
            return new ItemListAdapter(this.context, Collections.singletonList(new FullBinderFactory<Item>() {
                @NonNull
                @Override
                public Collection<BinderFactory<Item>> getParts(@Nullable final Item model) {
                    return ItemListAdapter.Builder.this.factories;
                }

                @Override
                public boolean isNeeded(@Nullable final Item model) {
                    return true;
                }
            }));
        }
    }

    public final class ItemListFilter extends DelegatingFilter {
        private List<String> categories;
        private Set<String>  sources;

        public void setCategories(@NonNull final List<String> categories) {
            this.categories = categories;
        }

        public void setSources(@NonNull final Set<String> sources) {
            this.sources = sources;
        }

        @SuppressWarnings("IllegalCatch")
        @NonNull
        @Override
        public FilterResults performFiltering(@Nullable final CharSequence searchText) {
            final FilterResults results = new FilterResults();

            try {
                final List<NewsItem> items = Single.<Realm>create(emitter -> emitter.onSuccess(DaggerDataComponent.builder()
                    .dataModule(new DataModule(ItemListAdapter.this.context))
                    .build()
                    .realm()))
                    .compose(RxUtils.applySingleSchedulers(DataManager.SCHEDULER))
                    .flatMap(realm -> new ItemManager(realm).getItems(searchText, this.sources.toArray(StringUtils.EMPTY_ARRAY), this.categories.toArray(StringUtils.EMPTY_ARRAY)))
                    .compose(RxUtils.applySingleSchedulers(DataManager.SCHEDULER))
                    .blockingGet();

                results.values = items;
                results.count  = items.size();
            } catch (final Throwable e) {
                if (TestUtils.isLoggable()) Log.e(this.getClass().getSimpleName(), e.getMessage(), e);
            }

            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void publishResults(@Nullable final CharSequence searchText, @Nullable final FilterResults results) {
            ItemListAdapter.this.clear();

            if (results != null && results.values != null) {
                final List<Item> items = (List<Item>)results.values;
                if (results.count > 0) {
                    Collections.sort(items);

                    final List<Item> featuredItems = new ArrayList<>(items);
                    featuredItems.add(0, FeaturedItem.create(items));

                    ItemListAdapter.this.onDataSetChanged(featuredItems);
                }
            }
        }
    }

    private final Context context;

    private Filter filter;

    private ItemListAdapter(@NonNull final Context context, @NonNull final List<FullBinderFactory<Item>> factories) {
        super(factories);

        this.context = context;
    }

    @NonNull
    @Override
    protected Iterable<Animator> getItemAnimators(@NonNull final View view) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled() ? super.getItemAnimators(view) : Animations.createDefaultAnimators(view);
    }

    @Override
    public SimpleViewHolder<View> onCreateViewHolder(final ViewGroup parent, final int viewType) {
        switch (viewType) {
            case FeaturedView.VIEW_TYPE:
                return new SimpleViewHolder<>(new FeaturedView(this.context));

            case HeaderView.VIEW_TYPE:
                return new SimpleViewHolder<>(new HeaderView(this.context));

            case ImageView.VIEW_TYPE:
                return new SimpleViewHolder<>(new ImageView(this.context));

            case FooterView.VIEW_TYPE:
                return new SimpleViewHolder<>(new FooterView(this.context));

            case ContentView.VIEW_TYPE:
                return new SimpleViewHolder<>(new ContentView(this.context));

            case MetaView.VIEW_TYPE:
                return new SimpleViewHolder<>(new MetaView(this.context));

            default:
                throw new IllegalArgumentException("Unsupported view type: " + viewType);
        }
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return this.filter == null ? this.filter = new ItemListAdapter.ItemListFilter() : this.filter;
    }
}
