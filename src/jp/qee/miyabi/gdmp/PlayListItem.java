package jp.qee.miyabi.gdmp;

public class PlayListItem {
	private String mId;
	private int mPosition;
	private String mFileTitle;
	private int mFileSize;
	private String mTitle;
	private String mArtist;
	private byte[] mCoverImage;
	
	public PlayListItem(String id, int position, String fileTitle, int size) {
//	public PlaylistItem(String id, String fileTitle, int size) {
		mId = id;
		mPosition = position;
		mFileTitle = fileTitle;
		mFileSize = size;
		mTitle = null;
		mArtist = null;
		setCoverImage(null);
	}
	
	public String getId() {
		return mId;
	}

	public int getPosition() {
		return mPosition;
	}

	public String getFileTitle() {
		return mFileTitle;
	}

	public int getFileSize() {
		return mFileSize;
	}

	public String getTitle() {
		if (mTitle != null) {
			return mTitle;
		}
		return mFileTitle;
	}

	public void setTitle(String mTitle) {
		this.mTitle = mTitle;
	}
	
	public String getArtist() {
		return mArtist;
	}
	
	public void setArtist(String mArtist) {
		this.mArtist = mArtist;
	}

	public byte[] getCoverImage() {
		return mCoverImage;
	}

	public void setCoverImage(byte[] mCoverImage) {
		this.mCoverImage = mCoverImage;
	}
}
