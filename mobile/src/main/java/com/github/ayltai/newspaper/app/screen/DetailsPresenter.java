package com.github.ayltai.newspaper.app.screen;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.Log;

import com.github.ayltai.newspaper.analytics.AnalyticsModule;
import com.github.ayltai.newspaper.analytics.DaggerAnalyticsComponent;
import com.github.ayltai.newspaper.analytics.ShareEvent;
import com.github.ayltai.newspaper.app.data.ItemManager;
import com.github.ayltai.newspaper.app.data.model.Image;
import com.github.ayltai.newspaper.app.data.model.Item;
import com.github.ayltai.newspaper.app.data.model.NewsItem;
import com.github.ayltai.newspaper.app.view.ItemPresenter;
import com.github.ayltai.newspaper.client.Client;
import com.github.ayltai.newspaper.client.ClientFactory;
import com.github.ayltai.newspaper.data.DataManager;
import com.github.ayltai.newspaper.net.NetworkUtils;
import com.github.ayltai.newspaper.util.Irrelevant;
import com.github.ayltai.newspaper.util.RxUtils;
import com.github.ayltai.newspaper.util.TestUtils;

import io.reactivex.Flowable;
import io.reactivex.Single;

public class DetailsPresenter extends ItemPresenter<DetailsPresenter.View> {
    public interface View extends ItemPresenter.View {
        @Nullable
        Flowable<Irrelevant> shareClicks();

        void share(@NonNull String url);

        void showImage(@NonNull String url);
    }

    @UiThread
    @Override
    public void bindModel(final Item model) {
        if (this.getView() == null) {
            super.bindModel(model);
        } else {
            if (model instanceof NewsItem) {
                final NewsItem newsItem = (NewsItem)model;

                if (newsItem.isFullDescription()) {
                    super.bindModel(model);

                    this.manageDisposable(DetailsPresenter.updateItem(this.getView().getContext(), newsItem)
                        .compose(RxUtils.applySingleBackgroundToMainSchedulers())
                        .subscribe(
                            items -> {
                            },
                            error -> {
                                if (TestUtils.isLoggable()) Log.e(this.getClass().getSimpleName(), error.getMessage(), error);
                            }));
                } else {
                    super.bindModel(model);

                    if (NetworkUtils.isOnline(this.getView().getContext())) {
                        this.manageDisposable(Single.<NewsItem>create(
                            emitter -> {
                                final Client client = ClientFactory.getInstance(this.getView().getContext()).getClient(model.getSource());

                                if (client == null) {
                                    emitter.onError(new IllegalArgumentException("Unrecognized source " + model.getSource()));
                                } else {
                                    client.updateItem((NewsItem)model).subscribe(emitter::onSuccess);
                                }
                            })
                            .compose(RxUtils.applySingleBackgroundSchedulers())
                            .flatMap(item -> DetailsPresenter.updateItem(this.getView().getContext(), item)).compose(RxUtils.applySingleBackgroundToMainSchedulers())
                            .subscribe(
                                items -> super.bindModel(items.get(0)),
                                error -> {
                                    if (TestUtils.isLoggable()) Log.e(this.getClass().getSimpleName(), error.getMessage(), error);
                                }));
                    }
                }
            } else {
                super.bindModel(model);
            }
        }
    }

    @CallSuper
    @Override
    protected void onAvatarClick() {
        // TODO

        super.onAvatarClick();
    }

    @CallSuper
    @Override
    protected void onSourceClick() {
        // TODO

        super.onSourceClick();
    }

    @CallSuper
    @Override
    protected void onBookmarkClick() {
        if (this.getView() != null && this.getModel() instanceof NewsItem) {
            final NewsItem item = (NewsItem)this.getModel();
            item.setBookmarked(!this.getModel().isBookmarked());
            item.setLastAccessedDate(new Date());

            this.manageDisposable(DetailsPresenter.updateItem(this.getView().getContext(), item)
                .compose(RxUtils.applySingleSchedulers(DataManager.SCHEDULER))
                .subscribe(
                    items -> {
                    },
                    error -> {
                        if (TestUtils.isLoggable()) Log.e(this.getClass().getSimpleName(), error.getMessage(), error);
                    }
                ));
        }

        if (this.getView() != null) this.getView().setIsBookmarked(this.getModel().isBookmarked());

        super.onBookmarkClick();
    }

    protected void onShareClick() {
        if (this.getView() != null) {
            DaggerAnalyticsComponent.builder()
                .analyticsModule(new AnalyticsModule(this.getView().getContext()))
                .build()
                .eventLogger()
                .logEvent(new ShareEvent()
                    .setSource(this.getModel().getSource())
                    .setCategory(this.getModel().getCategory()));

            this.getView().share(this.getModel().getLink());
        }
    }

    @CallSuper
    @Override
    protected void onImageClick(@NonNull final Image image) {
        if (this.getView() != null) this.getView().showImage(image.getUrl());

        super.onImageClick(image);
    }

    @CallSuper
    @Override
    protected void onVideoClick() {
        // TODO

        super.onVideoClick();
    }

    @CallSuper
    @Override
    public void onViewAttached(@NonNull final DetailsPresenter.View view, final boolean isFirstTimeAttachment) {
        final Flowable<Irrelevant> shareClicks = view.shareClicks();
        if (shareClicks != null) this.manageDisposable(shareClicks.subscribe(irrelevant -> this.onShareClick()));

        super.onViewAttached(view, isFirstTimeAttachment);
    }

    private static Single<List<NewsItem>> updateItem(@NonNull final Context context, @NonNull final NewsItem item) {
        item.setLastAccessedDate(new Date());

        return ItemManager.create(context)
            .compose(RxUtils.applySingleSchedulers(DataManager.SCHEDULER))
            .flatMap(manager -> manager.putItems(Collections.singletonList(item))
                .compose(RxUtils.applySingleSchedulers(DataManager.SCHEDULER)));
    }
}
