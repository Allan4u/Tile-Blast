package com.allan.tileblast.daily;

/**
 * One day's record in the daily-challenge calendar view.
 *
 * <p>Fields are public for simple data access (consistent with other
 * lightweight data classes in this codebase, e.g. {@code Piece}).
 */
public class DayEntry {

    /** Date key in YYYYMMDD format, e.g. {@code "20250715"}. */
    public String dateKey;

    /** Best score recorded for the day. {@code 0} indicates no attempt. */
    public int score;

    /** True when the player earned a star for the day. */
    public boolean starred;

    /** True when this entry is the device's local "today". */
    public boolean isToday;

    public DayEntry() { }

    public DayEntry(String dateKey, int score, boolean starred, boolean isToday) {
        this.dateKey = dateKey;
        this.score = score;
        this.starred = starred;
        this.isToday = isToday;
    }
}
