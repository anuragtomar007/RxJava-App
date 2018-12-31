package com.example.dell.rxjavaapp;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.dell.rxjavaapp.network.ApiClient;
import com.example.dell.rxjavaapp.network.ApiService;
import com.example.dell.rxjavaapp.network.model.Note;
import com.example.dell.rxjavaapp.network.model.User;
import com.example.dell.rxjavaapp.utils.MyDividerItemDecoration;
import com.example.dell.rxjavaapp.utils.PrefUtils;
import com.example.dell.rxjavaapp.utils.RecyclerTouchListener;
import com.example.dell.rxjavaapp.view.NotesAdapter;
import com.jakewharton.retrofit2.adapter.rxjava2.HttpException;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private ApiService apiService;
    private CompositeDisposable disposable = new CompositeDisposable();
    private NotesAdapter mAdapter;
    private List<Note> notesList = new ArrayList<>();

    @BindView(R.id.coordinator_layout)
    CoordinatorLayout coordinatorLayout;
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;
    @BindView(R.id.txt_empty_notes_view)
    TextView noNotesView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNoteDialog(false, null, -1);
            }
        });
        whiteNotificationBar(fab);
        apiService = ApiClient.getClient(getApplicationContext()).create(ApiService.class);

        mAdapter = new NotesAdapter(this, notesList);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new MyDividerItemDecoration(this, LinearLayoutManager.VERTICAL, 16));
        recyclerView.setAdapter(mAdapter);

        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(this,
                recyclerView, new RecyclerTouchListener.ClickListener() {
            @Override
            public void onClick(View view, final int position) {
            }

            @Override
            public void onLongClick(View view, int position) {
                showActionsDialog(position);
            }
        }));

        if (TextUtils.isEmpty(PrefUtils.getApiKey(this))) {
            registerUser();
        } else {
            fetchAllNotes();
        }
    }

    private void registerUser() {
        String uniqueId = UUID.randomUUID().toString();
        disposable.add(apiService.register(uniqueId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<User>() {
                    @Override
                    public void onSuccess(User user) {
                        PrefUtils.storeApiKey(getApplicationContext(), user.getApiKey());
                        Toast.makeText(getApplicationContext(), "Device is registered successfully! ApiKey: " + PrefUtils.getApiKey(getApplicationContext()), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError: " + e.getMessage());
                        showError(e);
                    }
                }));
    }

    private void fetchAllNotes() {
        disposable.add(apiService.fetchAllNotes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(new Function<List<Note>, List<Note>>() {
                    @Override
                    public List<Note> apply(List<Note> notes) {
                        Collections.sort(notes, new Comparator<Note>() {
                            @Override
                            public int compare(Note n1, Note n2) {
                                return n2.getId() - n1.getId();
                            }
                        });
                        return notes;
                    }
                })
                .subscribeWith(new DisposableSingleObserver<List<Note>>() {
                    @Override
                    public void onSuccess(List<Note> notes) {
                        notesList.clear();
                        notesList.addAll(notes);
                        mAdapter.notifyDataSetChanged();
                        toggleEmptyNotes();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError: " + e.getMessage());
                        showError(e);
                    }
                })
        );
    }

    private void createNote(String note) {
        disposable.add(apiService.createNote(note)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<Note>() {

                    @Override
                    public void onSuccess(Note note) {
                        if (!TextUtils.isEmpty(note.getError())) {
                            Toast.makeText(getApplicationContext(), note.getError(), Toast.LENGTH_LONG).show();
                            return;
                        }
                        Log.d(TAG, "new note created: " + note.getId() + ", " + note.getNote() + ", " + note.getTimestamp());
                        notesList.add(0, note);
                        mAdapter.notifyItemInserted(0);
                        toggleEmptyNotes();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError: " + e.getMessage());
                        showError(e);
                    }
                }));
    }

    private void updateNote(int noteId, final String note, final int position) {
        disposable.add(apiService.updateNote(noteId, note)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver() {
                    @Override
                    public void onComplete() {
                        Log.d(TAG, "Note updated!");
                        Note n = notesList.get(position);
                        n.setNote(note);
                        notesList.set(position, n);
                        mAdapter.notifyItemChanged(position);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError: " + e.getMessage());
                        showError(e);
                    }
                }));
    }

    private void deleteNote(final int noteId, final int position) {
        Log.e(TAG, "deleteNote: " + noteId + ", " + position);
        disposable.add(apiService.deleteNote(noteId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver() {
                    @Override
                    public void onComplete() {
                        Log.d(TAG, "Note deleted! " + noteId);
                        notesList.remove(position);
                        mAdapter.notifyItemRemoved(position);
                        Toast.makeText(MainActivity.this, "Note deleted!", Toast.LENGTH_SHORT).show();
                        toggleEmptyNotes();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError: " + e.getMessage());
                        showError(e);
                    }
                })
        );
    }

    private void showNoteDialog(final boolean shouldUpdate, final Note note, final int position) {
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getApplicationContext());
        View view = layoutInflaterAndroid.inflate(R.layout.note_dialog, null);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilderUserInput.setView(view);
        final EditText inputNote = view.findViewById(R.id.note);
        TextView dialogTitle = view.findViewById(R.id.dialog_title);
        dialogTitle.setText(!shouldUpdate ? getString(R.string.lbl_new_note_title) : getString(R.string.lbl_edit_note_title));
        if (shouldUpdate && note != null) {
            inputNote.setText(note.getNote());
        }
        alertDialogBuilderUserInput
                .setCancelable(false)
                .setPositiveButton(shouldUpdate ? "update" : "save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogBox, int id) {
                    }
                })
                .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogBox, int id) {
                        dialogBox.cancel();
                    }
                });

        final AlertDialog alertDialog = alertDialogBuilderUserInput.create();
        alertDialog.show();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TextUtils.isEmpty(inputNote.getText().toString())) {
                    Toast.makeText(MainActivity.this, "Enter note!", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    alertDialog.dismiss();
                }
                if (shouldUpdate && note != null) {
                    updateNote(note.getId(), inputNote.getText().toString(), position);
                } else {
                    createNote(inputNote.getText().toString());
                }
            }
        });
    }

    private void showActionsDialog(final int position) {
        CharSequence colors[] = new CharSequence[]{"Edit", "Delete"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose option");
        builder.setItems(colors, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showNoteDialog(true, notesList.get(position), position);
                } else {
                    deleteNote(notesList.get(position).getId(), position);
                }
            }
        });
        builder.show();
    }

    private void toggleEmptyNotes() {
        if (notesList.size() > 0) {
            noNotesView.setVisibility(View.GONE);
        } else {
            noNotesView.setVisibility(View.VISIBLE);
        }
    }

    private void showError(Throwable e) {
        String message = "";
        try {
            if (e instanceof IOException) {
                message = "No internet connection!";
            } else if (e instanceof HttpException) {
                HttpException error = (HttpException) e;
                String errorBody = error.response().errorBody().string();
                JSONObject jObj = new JSONObject(errorBody);
                message = jObj.getString("error");
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        if (TextUtils.isEmpty(message)) {
            message = "Unknown error occurred! Check LogCat.";
        }

        Snackbar snackbar = Snackbar
                .make(coordinatorLayout, message, Snackbar.LENGTH_LONG);

        View sbView = snackbar.getView();
        TextView textView = sbView.findViewById(android.support.design.R.id.snackbar_text);
        textView.setTextColor(Color.YELLOW);
        snackbar.show();
    }

    private void whiteNotificationBar(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = view.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            view.setSystemUiVisibility(flags);
            getWindow().setStatusBarColor(Color.WHITE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposable.dispose();
    }
}