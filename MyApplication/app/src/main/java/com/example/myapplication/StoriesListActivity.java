package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoriesListActivity extends AppCompatActivity {

    private ListView storiesListView;
    private ImageButton backButton;
    private LinearLayout emptyStateContainer;
    private Button openCalendarButton;
    private com.google.android.material.floatingactionbutton.FloatingActionButton calendarFab;
    private EditText searchEditText;
    private TextView subtitleText;
    private TextView sortButton;
    private TextView filterAll;
    private TextView filterWeek;
    private TextView filterMonth;
    private TextView filterYear;

    private StoriesAdapter adapter;
    private final List<StoryPreview> allPreviews = new ArrayList<>();
    private final List<StoryPreview> filteredPreviews = new ArrayList<>();
    private final List<String> storyDateKeys = new ArrayList<>();

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private enum TimeFilter { ALL, WEEK, MONTH, YEAR }
    private TimeFilter selectedTimeFilter = TimeFilter.ALL;
    private boolean sortNewestFirst = true;
    private String currentQuery = "";

    private static final Pattern INLINE_IMAGE_PLACEHOLDER_ANY_PATTERN = Pattern.compile("\\[IMG:[^\\]]+\\]", Pattern.DOTALL);
    private static final Pattern INLINE_IMAGE_FIRST_PATTERN = Pattern.compile("\\[IMG:([^\\]:]+)(?::\\d+:\\d+)?\\]", Pattern.DOTALL);
    private static final Pattern DATE_KEY_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final String WALLPAPER_MARKER = "[WALLPAPER_MARKER]";
    private static final String MOOD_MARKER = "[MOOD_MARKER]";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StoryStore.migrateIfNeeded(this);

        setContentView(R.layout.activity_stories_list);
        
        storiesListView = findViewById(R.id.storiesListView);
        backButton = findViewById(R.id.backButton);
        emptyStateContainer = findViewById(R.id.emptyStateContainer);
        openCalendarButton = findViewById(R.id.openCalendarButton);
        calendarFab = findViewById(R.id.calendarFab);

        searchEditText = findViewById(R.id.searchEditText);
        subtitleText = findViewById(R.id.subtitleText);
        sortButton = findViewById(R.id.sortButton);
        filterAll = findViewById(R.id.filterAll);
        filterWeek = findViewById(R.id.filterWeek);
        filterMonth = findViewById(R.id.filterMonth);
        filterYear = findViewById(R.id.filterYear);
        
        adapter = new StoriesAdapter();
        storiesListView.setAdapter(adapter);

        // Load stories after first frame to avoid navigation jank.
        if (storiesListView != null) {
            storiesListView.post(this::loadAllStories);
        } else {
            loadAllStories();
        }

        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    currentQuery = s != null ? s.toString() : "";
                    applyFiltersAndSort();
                }
            });
        }

        if (sortButton != null) {
            sortButton.setOnClickListener(v -> {
                sortNewestFirst = !sortNewestFirst;
                sortButton.setText(sortNewestFirst ? "Newest" : "Oldest");
                applyFiltersAndSort();
            });
        }

        if (filterAll != null) filterAll.setOnClickListener(v -> setTimeFilter(TimeFilter.ALL));
        if (filterWeek != null) filterWeek.setOnClickListener(v -> setTimeFilter(TimeFilter.WEEK));
        if (filterMonth != null) filterMonth.setOnClickListener(v -> setTimeFilter(TimeFilter.MONTH));
        if (filterYear != null) filterYear.setOnClickListener(v -> setTimeFilter(TimeFilter.YEAR));

        setTimeFilter(TimeFilter.ALL);
        
        // Set item click listener to view story details
        storiesListView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filteredPreviews.size()) return;
            StoryPreview preview = filteredPreviews.get(position);
            String dateKey = preview != null ? preview.dateKey : null;
            if (dateKey == null || dateKey.isEmpty()) return;

            // dateKey format: YYYY-MM-DD
            String[] parts = dateKey.split("-");
            if (parts.length != 3) return;

            try {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]) - 1; // 0-11
                int day = Integer.parseInt(parts[2]);

                Intent intent = new Intent(StoriesListActivity.this, StoryDetailActivity.class);
                intent.putExtra("day", day);
                intent.putExtra("month", month);
                intent.putExtra("year", year);
                startActivity(intent);
            } catch (NumberFormatException ignored) {
                // Ignore invalid date key
            }
        });
        
        // Back button
        backButton.setOnClickListener(v -> finish());

        if (openCalendarButton != null) {
            openCalendarButton.setOnClickListener(v -> {
                openCalendar();
            });
        }

        if (calendarFab != null) {
            calendarFab.setOnClickListener(v -> openCalendar());
        }
    }

    private void openCalendar() {
        Intent intent = new Intent(StoriesListActivity.this, CalendarActivity.class);
        startActivity(intent);
    }
    
    private void loadAllStories() {
        backgroundExecutor.execute(() -> {
            SharedPreferences sharedPref = StoryStore.get(this);
            Map<String, ?> allStories = sharedPref.getAll();

            List<StoryPreview> previews = new ArrayList<>();
            List<String> dateKeys = new ArrayList<>();
            boolean isEmpty = allStories.isEmpty();

            if (!isEmpty) {
                // Sort dates in reverse order (newest first)
                List<String> dates = new ArrayList<>(allStories.keySet());
                Collections.sort(dates, Collections.reverseOrder());

                for (String dateKey : dates) {
                    if (dateKey == null || !DATE_KEY_PATTERN.matcher(dateKey).matches()) {
                        // Skip EncryptedSharedPreferences internal keys and any other non-date keys.
                        continue;
                    }

                    Object raw = allStories.get(dateKey);
                    if (!(raw instanceof String)) {
                        // Defensive: skip non-string preference values.
                        continue;
                    }

                    String storyData = (String) raw;
                    if (storyData != null) {
                        String moodText = null;
                        String moodId = extractMoodId(storyData);
                        if (moodId != null && !moodId.trim().isEmpty()) {
                            Mood mood = Mood.findById(moodId.trim());
                            if (mood != null) {
                                String emoji = mood.emoji != null ? mood.emoji.trim() : "";
                                String label = mood.label != null ? mood.label.trim() : "";
                                if (!emoji.isEmpty() && !label.isEmpty()) {
                                    moodText = emoji + " " + label;
                                } else if (!emoji.isEmpty()) {
                                    moodText = emoji;
                                } else if (!label.isEmpty()) {
                                    moodText = label;
                                }
                            } else {
                                moodText = moodId.trim();
                            }
                        }

                        // Extract wallpaper and mood markers BEFORE parsing legacy title/story/goals.
                        boolean hasWallpaper = false;
                        String storyDataForPreview = storyData;
                        int wpIndex = storyDataForPreview.indexOf(WALLPAPER_MARKER);
                        if (wpIndex >= 0) {
                            hasWallpaper = true;
                            storyDataForPreview = storyDataForPreview.substring(0, wpIndex);
                        }

                        int moodIndex = storyDataForPreview.indexOf(MOOD_MARKER);
                        if (moodIndex >= 0) {
                            storyDataForPreview = storyDataForPreview.substring(0, moodIndex);
                        }

                        // Legacy format: title||story||goals (goals separated by |||)
                        // IMPORTANT: do not split("||") because "|||" contains "||".
                        String title = "";
                        String story = "";
                        String goals = "";
                        int firstSep = storyDataForPreview.indexOf("||");
                        if (firstSep < 0) {
                            title = storyDataForPreview;
                        } else {
                            title = storyDataForPreview.substring(0, firstSep);
                            int secondSep = storyDataForPreview.indexOf("||", firstSep + 2);
                            if (secondSep < 0) {
                                story = storyDataForPreview.substring(firstSep + 2);
                            } else {
                                story = storyDataForPreview.substring(firstSep + 2, secondSep);
                                goals = storyDataForPreview.substring(secondSep + 2);
                            }
                        }

                        // Remove inline image placeholders from preview text
                        boolean hasInlinePhoto = story != null && story.contains("[IMG:");

                        String storyTextNoImages = INLINE_IMAGE_PLACEHOLDER_ANY_PATTERN.matcher(story).replaceAll("");
                        storyTextNoImages = storyTextNoImages != null ? storyTextNoImages.trim() : "";
                        // Collapse newlines/tabs/multiple spaces so previews don't become very tall.
                        storyTextNoImages = storyTextNoImages.replaceAll("\\s+", " ").trim();

                        String titleTrim = title != null ? title.trim() : "";
                        String snippet;
                        if (storyTextNoImages.isEmpty()) {
                            snippet = "No content written yet.";
                        } else {
                            snippet = storyTextNoImages.length() > 120 ? storyTextNoImages.substring(0, 120) + "..." : storyTextNoImages;
                        }

                        StoryPreview preview = StoryPreview.from(dateKey, titleTrim, snippet);
                        preview.hasWallpaper = hasWallpaper;
                        preview.hasInlinePhoto = hasInlinePhoto;
                        preview.moodText = moodText;
                        previews.add(preview);
                        dateKeys.add(dateKey);
                    }
                }
            }

            mainHandler.post(() -> {
                allPreviews.clear();
                filteredPreviews.clear();
                storyDateKeys.clear();
                allPreviews.addAll(previews);
                storyDateKeys.addAll(dateKeys);

                if (isEmpty) {
                    if (storiesListView != null) storiesListView.setVisibility(android.view.View.GONE);
                    if (emptyStateContainer != null) emptyStateContainer.setVisibility(android.view.View.VISIBLE);
                } else {
                    if (emptyStateContainer != null) emptyStateContainer.setVisibility(android.view.View.GONE);
                    if (storiesListView != null) storiesListView.setVisibility(android.view.View.VISIBLE);
                }

                applyFiltersAndSort();
            });
        });
    }

    private void setTimeFilter(TimeFilter filter) {
        selectedTimeFilter = filter;
        updateFilterChipStates();
        applyFiltersAndSort();
    }

    private void updateFilterChipStates() {
        if (filterAll == null || filterWeek == null || filterMonth == null || filterYear == null) return;

        filterAll.setSelected(selectedTimeFilter == TimeFilter.ALL);
        filterWeek.setSelected(selectedTimeFilter == TimeFilter.WEEK);
        filterMonth.setSelected(selectedTimeFilter == TimeFilter.MONTH);
        filterYear.setSelected(selectedTimeFilter == TimeFilter.YEAR);

        filterAll.setTextColor(getResources().getColor(selectedTimeFilter == TimeFilter.ALL ? R.color.menu_text_primary : R.color.menu_text_secondary));
        filterWeek.setTextColor(getResources().getColor(selectedTimeFilter == TimeFilter.WEEK ? R.color.menu_text_primary : R.color.menu_text_secondary));
        filterMonth.setTextColor(getResources().getColor(selectedTimeFilter == TimeFilter.MONTH ? R.color.menu_text_primary : R.color.menu_text_secondary));
        filterYear.setTextColor(getResources().getColor(selectedTimeFilter == TimeFilter.YEAR ? R.color.menu_text_primary : R.color.menu_text_secondary));
    }

    private void applyFiltersAndSort() {
        List<StoryPreview> timeFiltered = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        Calendar start = (Calendar) now.clone();
        Calendar end = (Calendar) now.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);

        if (selectedTimeFilter == TimeFilter.WEEK) {
            // Standard week scope: Monday -> Sunday (not locale-dependent).
            Calendar weekStart = (Calendar) now.clone();
            weekStart.set(Calendar.HOUR_OF_DAY, 0);
            weekStart.set(Calendar.MINUTE, 0);
            weekStart.set(Calendar.SECOND, 0);
            weekStart.set(Calendar.MILLISECOND, 0);

            int dow = weekStart.get(Calendar.DAY_OF_WEEK);
            int deltaToMonday = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
            weekStart.add(Calendar.DAY_OF_MONTH, deltaToMonday);

            Calendar weekEnd = (Calendar) weekStart.clone();
            weekEnd.add(Calendar.DAY_OF_MONTH, 6);
            weekEnd.set(Calendar.HOUR_OF_DAY, 23);
            weekEnd.set(Calendar.MINUTE, 59);
            weekEnd.set(Calendar.SECOND, 59);
            weekEnd.set(Calendar.MILLISECOND, 999);

            start = weekStart;
            end = weekEnd;
        } else if (selectedTimeFilter == TimeFilter.MONTH) {
            start.set(Calendar.DAY_OF_MONTH, 1);
            end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);
            end.add(Calendar.MILLISECOND, -1);
        } else if (selectedTimeFilter == TimeFilter.YEAR) {
            start.set(Calendar.MONTH, Calendar.JANUARY);
            start.set(Calendar.DAY_OF_MONTH, 1);
            end = (Calendar) start.clone();
            end.add(Calendar.YEAR, 1);
            end.add(Calendar.MILLISECOND, -1);
        }

        for (StoryPreview p : allPreviews) {
            if (p == null) continue;
            if (selectedTimeFilter == TimeFilter.ALL) {
                timeFiltered.add(p);
            } else {
                Calendar c = p.asCalendar();
                if (c != null && !c.before(start) && !c.after(end)) {
                    timeFiltered.add(p);
                }
            }
        }

        String q = currentQuery != null ? currentQuery.trim().toLowerCase(Locale.US) : "";
        filteredPreviews.clear();
        if (q.isEmpty()) {
            filteredPreviews.addAll(timeFiltered);
        } else {
            for (StoryPreview p : timeFiltered) {
                if (p.searchText != null && p.searchText.contains(q)) {
                    filteredPreviews.add(p);
                }
            }
        }

        Collections.sort(filteredPreviews, (a, b) -> {
            if (a == null || b == null) return 0;
            int cmp = a.dateKey.compareTo(b.dateKey);
            return sortNewestFirst ? -cmp : cmp;
        });

        updateSubtitle(filteredPreviews.size());
        adapter.notifyDataSetChanged();
    }

    private void updateSubtitle(int filteredCount) {
        if (subtitleText == null) return;

        Calendar now = Calendar.getInstance();
        String month = now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.US);
        int year = now.get(Calendar.YEAR);
        String countLabel = filteredCount == 1 ? "1 entry" : (filteredCount + " entries");

        if (selectedTimeFilter == TimeFilter.ALL) {
            subtitleText.setText(month + " " + year + " • " + countLabel);
        } else if (selectedTimeFilter == TimeFilter.WEEK) {
            subtitleText.setText(month + " " + year + " • " + countLabel);
        } else if (selectedTimeFilter == TimeFilter.MONTH) {
            subtitleText.setText(month + " " + year + " • " + countLabel);
        } else {
            subtitleText.setText(String.valueOf(year) + " • " + countLabel);
        }
    }

    private static String extractFirstInlineImageBase64(String story) {
        if (story == null || story.isEmpty()) return null;
        Matcher m = INLINE_IMAGE_FIRST_PATTERN.matcher(story);
        if (!m.find()) return null;
        String base64 = m.group(1);
        if (base64 == null) return null;
        return base64.trim();
    }

    private static String extractWallpaperBase64(String storyData) {
        if (storyData == null || storyData.isEmpty()) return null;
        int wpIndex = storyData.indexOf(WALLPAPER_MARKER);
        if (wpIndex < 0) return null;
        String b64 = storyData.substring(wpIndex + WALLPAPER_MARKER.length());
        return b64 != null ? b64.trim() : null;
    }

    private static String extractMoodId(String storyData) {
        if (storyData == null) return null;
        int moodIndex = storyData.indexOf(MOOD_MARKER);
        if (moodIndex < 0) return null;

        int start = moodIndex + MOOD_MARKER.length();
        int end = storyData.length();
        int wpIndex = storyData.indexOf(WALLPAPER_MARKER, start);
        if (wpIndex >= 0) end = Math.min(end, wpIndex);
        String moodId = storyData.substring(start, end);
        return moodId != null ? moodId.trim() : null;
    }

    private static Bitmap decodeBase64ToScaledBitmap(String base64, int reqWidthPx, int reqHeightPx) {
        if (base64 == null || base64.trim().isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

            int inSampleSize = 1;
            int height = opts.outHeight;
            int width = opts.outWidth;
            if (height > reqHeightPx || width > reqWidthPx) {
                int halfHeight = height / 2;
                int halfWidth = width / 2;
                while ((halfHeight / inSampleSize) >= reqHeightPx && (halfWidth / inSampleSize) >= reqWidthPx) {
                    inSampleSize *= 2;
                }
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = Math.max(1, inSampleSize);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;

            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        } catch (OutOfMemoryError oom) {
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class StoryPreview {
        final String dateKey;
        final int day;
        final String monthLabel;
        final int year;
        final String title;
        final String snippet;
        final String searchText;

        boolean hasWallpaper;
        boolean hasInlinePhoto;
        String moodText;

        private StoryPreview(String dateKey, int day, String monthLabel, int year, String title, String snippet, String searchText) {
            this.dateKey = dateKey;
            this.day = day;
            this.monthLabel = monthLabel;
            this.year = year;
            this.title = title;
            this.snippet = snippet;
            this.searchText = searchText;
        }

        static StoryPreview from(String dateKey, String title, String snippet) {
            int y = 0, m = 0, d = 0;
            try {
                String[] parts = dateKey.split("-");
                if (parts.length == 3) {
                    y = Integer.parseInt(parts[0]);
                    m = Integer.parseInt(parts[1]);
                    d = Integer.parseInt(parts[2]);
                }
            } catch (Exception ignored) {
            }
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, y);
            c.set(Calendar.MONTH, Math.max(0, m - 1));
            c.set(Calendar.DAY_OF_MONTH, Math.max(1, d));
            String monthLabel = c.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US);
            if (monthLabel == null) monthLabel = "";

            String safeTitle = title == null || title.trim().isEmpty() ? "Untitled" : title.trim();
            String safeSnippet = snippet == null ? "" : snippet;
            String search = (dateKey + " " + safeTitle + " " + safeSnippet).toLowerCase(Locale.US);

            return new StoryPreview(dateKey, d, monthLabel, y, safeTitle, safeSnippet, search);
        }

        Calendar asCalendar() {
            try {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.YEAR, year);
                c.set(Calendar.MONTH, Math.max(0, monthNumber0()));
                c.set(Calendar.DAY_OF_MONTH, Math.max(1, day));
                c.set(Calendar.HOUR_OF_DAY, 12);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                return c;
            } catch (Exception ignored) {
                return null;
            }
        }

        private int monthNumber0() {
            // We don't store numeric month separately, so infer from dateKey.
            try {
                String[] parts = dateKey.split("-");
                if (parts.length == 3) {
                    int m = Integer.parseInt(parts[1]);
                    return m - 1;
                }
            } catch (Exception ignored) {
            }
            return 0;
        }
    }

    private final class StoriesAdapter extends BaseAdapter {
        private final LayoutInflater inflater = LayoutInflater.from(StoriesListActivity.this);
        private final LruCache<String, Bitmap> bitmapCache = new LruCache<String, Bitmap>(24) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return 1;
            }
        };

        private void loadWallpaperAsync(String dateKey, String cacheKey, ImageView target) {
            target.setTag(cacheKey);
            imageExecutor.execute(() -> {
                Bitmap b = bitmapCache.get(cacheKey);
                if (b == null) {
                    String storyData = StoryStore.get(StoriesListActivity.this).getString(dateKey, null);
                    String b64 = extractWallpaperBase64(storyData);
                    if (b64 != null && !b64.isEmpty()) {
                        b = decodeBase64ToScaledBitmap(b64, 1080, 300);
                        if (b != null) bitmapCache.put(cacheKey, b);
                    }
                }
                final Bitmap result = b;
                mainHandler.post(() -> {
                    Object tag = target.getTag();
                    if (tag == null || !cacheKey.equals(tag)) return;
                    if (result != null) {
                        target.setVisibility(View.VISIBLE);
                        target.setImageBitmap(result);
                    } else {
                        target.setImageDrawable(null);
                        target.setVisibility(View.GONE);
                    }
                });
            });
        }

        private void loadInlineImageAsync(String dateKey, String cacheKey, ImageView target) {
            target.setTag(cacheKey);
            imageExecutor.execute(() -> {
                Bitmap b = bitmapCache.get(cacheKey);
                if (b == null) {
                    String storyData = StoryStore.get(StoriesListActivity.this).getString(dateKey, null);
                    // Strip markers to get story part
                    if (storyData != null) {
                        int wpIndex = storyData.indexOf(WALLPAPER_MARKER);
                        if (wpIndex >= 0) storyData = storyData.substring(0, wpIndex);
                        int moodIndex = storyData.indexOf(MOOD_MARKER);
                        if (moodIndex >= 0) storyData = storyData.substring(0, moodIndex);
                    }

                    String story = "";
                    if (storyData != null) {
                        int firstSep = storyData.indexOf("||");
                        if (firstSep >= 0) {
                            int secondSep = storyData.indexOf("||", firstSep + 2);
                            if (secondSep >= 0) {
                                story = storyData.substring(firstSep + 2, secondSep);
                            } else {
                                story = storyData.substring(firstSep + 2);
                            }
                        }
                    }

                    String b64 = extractFirstInlineImageBase64(story);
                    if (b64 != null && !b64.isEmpty()) {
                        b = decodeBase64ToScaledBitmap(b64, 1080, 600);
                        if (b != null) bitmapCache.put(cacheKey, b);
                    }
                }

                final Bitmap result = b;
                mainHandler.post(() -> {
                    Object tag = target.getTag();
                    if (tag == null || !cacheKey.equals(tag)) return;
                    if (result != null) {
                        target.setVisibility(View.VISIBLE);
                        target.setImageBitmap(result);
                    } else {
                        target.setImageDrawable(null);
                        target.setVisibility(View.GONE);
                    }
                });
            });
        }

        @Override
        public int getCount() {
            return filteredPreviews.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredPreviews.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            ViewHolder holder;
            if (view == null) {
                view = inflater.inflate(R.layout.item_story_card, parent, false);
                holder = new ViewHolder(view);
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            if (position < 0 || position >= filteredPreviews.size()) return view;
            StoryPreview p = filteredPreviews.get(position);
            if (p == null) return view;

            if (holder.dayText != null) holder.dayText.setText(String.format(Locale.US, "%02d", p.day));
            if (holder.monthText != null) holder.monthText.setText(p.monthLabel);
            if (holder.yearText != null) holder.yearText.setText(String.valueOf(p.year));
            if (holder.titleText != null) holder.titleText.setText(p.title);
            if (holder.snippetText != null) holder.snippetText.setText(p.snippet);

            // Mood badge (replaces the old "Photo" label)
            if (holder.photoBadge != null) {
                String mood = p.moodText != null ? p.moodText.trim() : "";
                if (mood.isEmpty()) {
                    holder.photoBadge.setText("");
                    holder.photoBadge.setVisibility(View.GONE);
                } else {
                    holder.photoBadge.setText(mood);
                    holder.photoBadge.setVisibility(View.VISIBLE);
                }
            }

            // Wallpaper header (top image)
            if (holder.headerImage != null) {
                if (p.hasWallpaper) {
                    String cacheKey = p.dateKey + "#wp";
                    Bitmap b = bitmapCache.get(cacheKey);
                    if (b == null) {
                        holder.headerImage.setImageDrawable(null);
                        holder.headerImage.setVisibility(View.GONE);
                        loadWallpaperAsync(p.dateKey, cacheKey, holder.headerImage);
                    } else {
                        holder.headerImage.setTag(cacheKey);
                    }
                    if (b != null) {
                        holder.headerImage.setVisibility(View.VISIBLE);
                        holder.headerImage.setImageBitmap(b);
                    } else {
                        holder.headerImage.setImageDrawable(null);
                        holder.headerImage.setVisibility(View.GONE);
                    }
                } else {
                    holder.headerImage.setImageDrawable(null);
                    holder.headerImage.setVisibility(View.GONE);
                }
            }

            // First inline image only
            if (holder.inlineImage != null) {
                if (p.hasInlinePhoto) {
                    String cacheKey = p.dateKey + "#in1";
                    Bitmap b = bitmapCache.get(cacheKey);
                    if (b == null) {
                        holder.inlineImage.setImageDrawable(null);
                        holder.inlineImage.setVisibility(View.GONE);
                        loadInlineImageAsync(p.dateKey, cacheKey, holder.inlineImage);
                    } else {
                        holder.inlineImage.setTag(cacheKey);
                    }
                    if (b != null) {
                        holder.inlineImage.setVisibility(View.VISIBLE);
                        holder.inlineImage.setImageBitmap(b);
                    } else {
                        holder.inlineImage.setImageDrawable(null);
                        holder.inlineImage.setVisibility(View.GONE);
                    }
                } else {
                    holder.inlineImage.setImageDrawable(null);
                    holder.inlineImage.setVisibility(View.GONE);
                }
            }

            return view;
        }

        private final class ViewHolder {
            final ImageView headerImage;
            final TextView dayText;
            final TextView monthText;
            final TextView yearText;
            final TextView photoBadge;
            final TextView titleText;
            final TextView snippetText;
            final ImageView inlineImage;

            ViewHolder(View root) {
                headerImage = root.findViewById(R.id.headerImage);
                dayText = root.findViewById(R.id.dayText);
                monthText = root.findViewById(R.id.monthText);
                yearText = root.findViewById(R.id.yearText);
                photoBadge = root.findViewById(R.id.photoBadge);
                titleText = root.findViewById(R.id.titleText);
                snippetText = root.findViewById(R.id.snippetText);
                inlineImage = root.findViewById(R.id.inlineImage);
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload stories when returning to this activity
        loadAllStories();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        backgroundExecutor.shutdownNow();
        imageExecutor.shutdownNow();
    }
}
