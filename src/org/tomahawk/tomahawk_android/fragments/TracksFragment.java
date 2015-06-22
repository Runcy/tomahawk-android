/* == This file is part of Tomahawk Player - <http://tomahawk-player.org> ===
 *
 *   Copyright 2012, Enno Gottschalk <mrmaffen@googlemail.com>
 *
 *   Tomahawk is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Tomahawk is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Tomahawk. If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomahawk.tomahawk_android.fragments;

import org.jdeferred.DoneCallback;
import org.tomahawk.libtomahawk.collection.AlphaComparator;
import org.tomahawk.libtomahawk.collection.ArtistAlphaComparator;
import org.tomahawk.libtomahawk.collection.Collection;
import org.tomahawk.libtomahawk.collection.CollectionManager;
import org.tomahawk.libtomahawk.collection.CollectionUtils;
import org.tomahawk.libtomahawk.collection.LastModifiedComparator;
import org.tomahawk.libtomahawk.collection.Playlist;
import org.tomahawk.libtomahawk.collection.Track;
import org.tomahawk.libtomahawk.collection.UserCollection;
import org.tomahawk.libtomahawk.database.DatabaseHelper;
import org.tomahawk.libtomahawk.resolver.Query;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.TomahawkApp;
import org.tomahawk.tomahawk_android.activities.TomahawkMainActivity;
import org.tomahawk.tomahawk_android.adapters.Segment;
import org.tomahawk.tomahawk_android.services.PlaybackService;
import org.tomahawk.tomahawk_android.views.FancyDropDown;

import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * {@link TomahawkFragment} which shows a set of {@link Track}s inside its {@link
 * se.emilsjolander.stickylistheaders.StickyListHeadersListView}
 */
public class TracksFragment extends TomahawkFragment {

    public static final String COLLECTION_TRACKS_SPINNER_POSITION
            = "org.tomahawk.tomahawk_android.collection_tracks_spinner_position";

    @SuppressWarnings("unused")
    public void onEventMainThread(CollectionManager.UpdatedEvent event) {
        super.onEventMainThread(event);

        if (event.mUpdatedItemIds != null && event.mUpdatedItemIds.contains(mAlbum.getCacheKey())) {
            showAlbumFancyDropDown();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mContainerFragmentClass == null) {
            getActivity().setTitle("");
        }
        updateAdapter();
    }

    /**
     * Called every time an item inside a ListView or GridView is clicked
     *
     * @param view the clicked view
     * @param item the Object which corresponds to the click
     */
    @Override
    public void onItemClick(View view, Object item) {
        if (item instanceof Query) {
            Query query = (Query) item;
            if (query.isPlayable()) {
                TomahawkMainActivity activity = (TomahawkMainActivity) getActivity();
                PlaybackService playbackService = activity.getPlaybackService();
                if (playbackService != null && playbackService.getCurrentQuery() == query) {
                    playbackService.playPause();
                } else {
                    Playlist playlist = Playlist.fromQueryList(DatabaseHelper.CACHED_PLAYLIST_NAME,
                            mShownQueries);
                    playlist.setId(DatabaseHelper.CACHED_PLAYLIST_ID);
                    if (playbackService != null) {
                        playbackService.setPlaylist(playlist, playlist.getEntryWithQuery(query));
                        Class clss = mContainerFragmentClass != null ? mContainerFragmentClass
                                : ((Object) this).getClass();
                        playbackService.setReturnFragment(clss, getArguments());
                        playbackService.start();
                    }
                }
            }
        }
    }

    /**
     * Update this {@link TomahawkFragment}'s {@link org.tomahawk.tomahawk_android.adapters.TomahawkListAdapter}
     * content
     */
    @Override
    protected void updateAdapter() {
        if (!mIsResumed) {
            return;
        }

        mResolveQueriesHandler.removeCallbacksAndMessages(null);
        mResolveQueriesHandler.sendEmptyMessage(RESOLVE_QUERIES_REPORTER_MSG);
        if (mAlbum != null) {
            mCollection.getAlbumTracks(mAlbum, true).done(
                    new DoneCallback<Set<Query>>() {
                        @Override
                        public void onDone(Set<Query> queries) {
                            mShownQueries = new ArrayList<>(queries);
                            Segment segment = new Segment(mAlbum.getArtist().getName(),
                                    new ArrayList<Object>(queries));
                            if (CollectionUtils.allFromOneArtist(queries)) {
                                segment.setHideArtistName(true);
                                segment.setShowDuration(true);
                            }
                            segment.setShowNumeration(true, 1);
                            fillAdapter(segment);
                            showContentHeader(mAlbum);
                            showAlbumFancyDropDown();
                        }
                    });
        } else if (mQuery != null) {
            mShownQueries = new ArrayList<>();
            mShownQueries.add(mQuery);
            Segment segment = new Segment(new ArrayList<Object>(mShownQueries));
            segment.setShowDuration(true);
            fillAdapter(segment);
            showContentHeader(mQuery);
            showFancyDropDown(mQuery.getName());
        } else if (mQueryArray != null) {
            mShownQueries = new ArrayList<>();
            mShownQueries.addAll(mQueryArray);
            Segment segment = new Segment(new ArrayList<Object>(mShownQueries));
            segment.setShowDuration(true);
            fillAdapter(segment);
        } else {
            Collection collection = mCollection != null ? mCollection
                    : CollectionManager.getInstance().getCollection(
                            TomahawkApp.PLUGINNAME_USERCOLLECTION);
            collection.getQueries().done(
                    new DoneCallback<Set<Query>>() {
                        @Override
                        public void onDone(Set<Query> queries) {
                            mShownQueries = new ArrayList<>(queries);
                            fillAdapter(
                                    new Segment(getDropdownPos(COLLECTION_TRACKS_SPINNER_POSITION),
                                            constructDropdownItems(),
                                            constructDropdownListener(
                                                    COLLECTION_TRACKS_SPINNER_POSITION),
                                            new ArrayList<Object>(sortAlbums(mShownQueries))));
                            showContentHeader(mAlbum);
                            showAlbumFancyDropDown();
                        }
                    });
        }
    }

    private List<Integer> constructDropdownItems() {
        List<Integer> dropDownItems = new ArrayList<>();
        dropDownItems.add(R.string.collection_dropdown_recently_added);
        dropDownItems.add(R.string.collection_dropdown_alpha);
        dropDownItems.add(R.string.collection_dropdown_alpha_artists);
        return dropDownItems;
    }

    private List<Query> sortAlbums(List<Query> queries) {
        switch (getDropdownPos(COLLECTION_TRACKS_SPINNER_POSITION)) {
            case 0:
                UserCollection userColl = (UserCollection) CollectionManager.getInstance()
                        .getCollection(TomahawkApp.PLUGINNAME_USERCOLLECTION);
                Collections.sort(queries, new LastModifiedComparator(
                        userColl.getQueryTimeStamps()));
                break;
            case 1:
                Collections.sort(queries, new AlphaComparator());
                break;
            case 2:
                Collections.sort(queries, new ArtistAlphaComparator());
                break;
        }
        return queries;
    }

    private void showAlbumFancyDropDown() {
        if (mAlbum != null) {
            CollectionManager.getInstance().getAvailableCollections(mAlbum).done(
                    new DoneCallback<List<Collection>>() {
                        @Override
                        public void onDone(final List<Collection> result) {
                            int initialSelection = 0;
                            for (int i = 0; i < result.size(); i++) {
                                if (result.get(i) == mCollection) {
                                    initialSelection = i;
                                    break;
                                }
                            }
                            showFancyDropDown(mAlbum.getName(), initialSelection,
                                    FancyDropDown.convertToDropDownItemInfo(result),
                                    new FancyDropDown.DropDownListener() {
                                        @Override
                                        public void onDropDownItemSelected(int position) {
                                            mCollection = result.get(position);
                                            updateAdapter();
                                        }

                                        @Override
                                        public void onCancel() {
                                        }
                                    });
                        }
                    });
        }
    }
}
