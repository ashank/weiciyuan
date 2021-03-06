package org.qii.weiciyuan.ui.preference.filter;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.SparseBooleanArray;
import android.view.*;
import android.view.animation.Animation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import org.qii.weiciyuan.R;
import org.qii.weiciyuan.support.lib.MyAsyncTask;
import org.qii.weiciyuan.support.utils.Utility;
import org.qii.weiciyuan.ui.animation.CollapseAnimation;
import org.qii.weiciyuan.ui.preference.AddFilterDialog;
import org.qii.weiciyuan.ui.preference.ModifyFilterDialog;

import java.util.*;

/**
 * User: qii
 * Date: 13-6-16
 */
public abstract class AbstractFilterFragment extends ListFragment {

    private BaseAdapter adapter;

    private DBTask task;

    private List<String> list = new ArrayList<String>();

    private RemoveFilterDBTask removeTask;


    @Override
    public void onDetach() {
        super.onDetach();
        if (task != null)
            task.cancel(true);

        if (removeTask != null)
            removeTask.cancel(true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        adapter = new FilterAdapter(getActivity(), getListView(), list);
        setListAdapter(adapter);
        if (task == null || task.getStatus() == MyAsyncTask.Status.FINISHED) {
            task = new DBTask();
            task.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);

        }
        getListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        getListView().setMultiChoiceModeListener(new FilterMultiChoiceModeListener());
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ModifyFilterDialog dialog = new ModifyFilterDialog(list.get(position));
                dialog.setTargetFragment(AbstractFilterFragment.this, 1);
                dialog.show(getFragmentManager(), "");
            }
        });

    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.actionbar_menu_filterfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add:
                AddFilterDialog dialog = new AddFilterDialog();
                dialog.setTargetFragment(AbstractFilterFragment.this, 0);
                dialog.show(getFragmentManager(), "");
                break;

        }

        return true;
    }


    public void addFilter(String word) {
        addFilterImpl(word);
        if (task == null || task.getStatus() == MyAsyncTask.Status.FINISHED) {
            task = new DBTask();
            task.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);

        }
    }


    class FilterMultiChoiceModeListener implements AbsListView.MultiChoiceModeListener {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.contextual_menu_draftfragment, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_remove:
                    SparseBooleanArray positions = getListView().getCheckedItemPositions();
                    long[] ids = getListView().getCheckedItemIds();
                    removeItem(positions, ids, mode);
                    return true;
            }
            return false;
        }


        @Override
        public void onDestroyActionMode(ActionMode mode) {

        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            mode.setTitle(String.format(getString(R.string.have_selected), String.valueOf(getListView().getCheckedItemCount())));
            adapter.notifyDataSetChanged();
        }
    }

    public void removeItem(SparseBooleanArray positions, final long[] ids, final ActionMode mode) {
        int size = positions.size();
        final List<Integer> positionList = new ArrayList<Integer>();
        for (int i = 0; i < size; i++) {
            if (positions.get(positions.keyAt(i))) {
                positionList.add(positions.keyAt(i));
            }
        }
        List<View> views = new ArrayList<View>();
        int start = getListView().getFirstVisiblePosition();
        int end = getListView().getLastVisiblePosition();

        for (Integer position : positionList) {
            if (position >= start && position <= end) {
                views.add(getListView().getChildAt((position - start)).findViewById(R.id.text1));
            }
        }
        List<Animation> animations = new ArrayList<Animation>();

        Animation.AnimationListener listener = new Animation.AnimationListener() {

            boolean finished = false;

            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (finished) {
                    return;
                }
                finished = true;
                Set<String> set = new HashSet<String>();
                for (long id : ids) {
                    set.add(list.get((int) id));
                }

                for (String name : set) {
                    Iterator<String> iterator = list.iterator();
                    while (iterator.hasNext()) {
                        String s = iterator.next();
                        if (s.equals(name)) {
                            iterator.remove();
                        }
                    }
                }


                mode.finish();

                if (Utility.isTaskStopped(removeTask)) {
                    removeTask = new RemoveFilterDBTask(set);
                    removeTask.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        };

        for (View view : views) {
            CollapseAnimation animation = new CollapseAnimation(view, 300);
            animation.setAnimationListener(listener);
            animations.add(animation);
            view.setAnimation(animation);
        }

        for (int i = 0; i < views.size(); i++) {
            views.get(i).startAnimation(animations.get(i));
        }


    }

    private class RemoveFilterDBTask extends MyAsyncTask<Void, List<String>, List<String>> {

        Set<String> set = new HashSet<String>();

        public RemoveFilterDBTask(Set<String> set) {
            this.set = set;
        }

        @Override
        protected List<String> doInBackground(Void... params) {
            return removeAndGetFilterListImpl(set);
        }

        @Override
        protected void onPostExecute(List<String> result) {
            list.clear();
            list.addAll(result);
            adapter.notifyDataSetChanged();
        }
    }

    class DBTask extends MyAsyncTask<Void, List<String>, List<String>> {

        @Override
        protected List<String> doInBackground(Void... params) {
            return getDBDataImpl();
        }

        @Override
        protected void onPostExecute(List<String> result) {
            super.onPostExecute(result);
            list.clear();
            list.addAll(result);
            adapter.notifyDataSetChanged();
        }
    }

    protected abstract List<String> getDBDataImpl();

    protected abstract void addFilterImpl(String value);

    protected abstract List<String> removeAndGetFilterListImpl(Set<String> set);

    public void modifyFilter(String oldWord, String newWord) {
        Set<String> set = new HashSet<String>();
        set.add(oldWord);
        removeAndGetFilterListImpl(set);
        addFilter(newWord);

    }
}
