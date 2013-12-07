package jp.qee.miyabi.gdmp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

public class GDriveUtils {
	private static Drive mService;
	private static GoogleAccountCredential mCredential;

	public static InputStream getDownloadStream(Context context, PlayListItem item) {
		Drive service = GDriveUtils.getDriveService(context);
		List<File> retList = new ArrayList<File>();

		Files.List request;
		String title = item.getFileTitle();
		title = title.replace("'", "\\'");

		try {
			request = service.files().list().setQ("title='" + title + "'");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		do {
			try {
				FileList files = request.execute();
				retList.addAll(files.getItems());
				request.setPageToken(files.getNextPageToken());
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} while (request.getPageToken() != null && request.getPageToken().length() > 0);

		for (File file : retList) {
			if (file.getId().equals(item.getId())) {
				try {
					HttpResponse resp = service.getRequestFactory().buildGetRequest(
							new GenericUrl(file.getDownloadUrl())).execute();
					return resp.getContent();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}
	
	public static Drive getDriveService(Context context) {
		mCredential = getCredential(context);

		if (null == mService) {
			mService = new Drive.Builder(
					AndroidHttp.newCompatibleTransport(), new GsonFactory(), mCredential)
				.setApplicationName(context.getResources().getString(R.string.app_name))
				.build();
		}

		return mService;
	}

	public static GoogleAccountCredential getCredential(Context context) {
		if (null == mCredential) {
			String accountName = AccountUtils.getAccountName(context);
			mCredential = GoogleAccountCredential.usingOAuth2(
					context, Arrays.asList(DriveScopes.DRIVE_READONLY));
			mCredential.setSelectedAccountName(accountName);
		}
		
		return mCredential;
	}
}
