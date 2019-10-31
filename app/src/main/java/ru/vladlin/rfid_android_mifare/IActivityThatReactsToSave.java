package ru.vladlin.rfid_android_mifare;

public interface IActivityThatReactsToSave {

    /**
     * This method will be called after a successful save process.
     */
    public abstract void onSaveSuccessful();

    /**
     * This method will be called, if there was an error during the
     * save process or it the user hits "cancel" in the "file already exists"
     * dialog.
     */
    public abstract void onSaveFailure();
}
