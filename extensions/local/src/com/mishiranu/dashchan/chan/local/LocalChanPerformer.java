package com.mishiranu.dashchan.chan.local;

import chan.content.ApiException;
import chan.content.ChanConfiguration;
import chan.content.ChanPerformer;
import chan.content.InvalidResponseException;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.HttpException;
import chan.http.HttpResponse;
import chan.text.ParseException;
import chan.util.DataFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LocalChanPerformer extends ChanPerformer {
	private static final Comparator<DataFile> LAST_MODIFIED_COMPARATOR =
			(lhs, rhs) -> Long.compare(rhs.getLastModified(), lhs.getLastModified());
	private static final int THREADS_PER_PAGE = 20;

	@Override
	public ReadThreadsResult onReadThreads(ReadThreadsData data) throws HttpException {
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		Thread thread = Thread.currentThread();
		ArrayList<Posts> threads = new ArrayList<>();
		int from = THREADS_PER_PAGE * data.pageNumber;
		int to = from + THREADS_PER_PAGE;
		int current = 0;
		DataFile localDownloadDirectory = configuration.getLocalDownloadDirectory();
		List<DataFile> files = localDownloadDirectory.getChildren();
		if (files != null) {
			Collections.sort(files, LAST_MODIFIED_COMPARATOR);
			for (DataFile file : files) {
				String name = file.getName();
				if (!file.isDirectory() && name.endsWith(".html")) {
					if (current >= from && current < to) {
						String threadNumber = name.substring(0, name.length() - 5);
						try (InputStream input = file.openInputStream()) {
							threads.add(new LocalPostsParser(this, threadNumber).convertThread(input));
						} catch (IOException | ParseException e) {
							// Ignore
						}
						if (thread.isInterrupted()) {
							return null;
						}
					}
					current++;
				}
			}
		}
		if (threads.size() == 0) {
			if (data.pageNumber == 0) {
				return null;
			} else {
				throw HttpException.createNotFoundException();
			}
		} else {
			return new ReadThreadsResult(threads);
		}
	}

	@Override
	public ReadPostsResult onReadPosts(ReadPostsData data) throws HttpException, InvalidResponseException {
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		DataFile localDownloadDirectory = configuration.getLocalDownloadDirectory();
		DataFile file = localDownloadDirectory.getChild(data.threadNumber + ".html");
		try (InputStream input = file.openInputStream()) {
			return new ReadPostsResult(new LocalPostsParser(this, data.threadNumber).convertPosts(input));
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		} catch (IOException e) {
			throw HttpException.createNotFoundException();
		}
	}

	@Override
	public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException {
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		String path = data.uri.getPath();
		if ("localhost".equals(data.uri.getAuthority()) && path != null) {
			DataFile file = configuration.getLocalDownloadDirectory().getChild(path);
			try {
				return new ReadContentResult(new HttpResponse(file.openInputStream()));
			} catch (IOException e) {
				throw HttpException.createNotFoundException();
			}
		} else {
			return super.onReadContent(data);
		}
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data)
			throws ApiException, InvalidResponseException {
		LocalChanConfiguration configuration = ChanConfiguration.get(this);
		DataFile localDownloadDirectory = configuration.getLocalDownloadDirectory();
		DataFile file = localDownloadDirectory.getChild(data.threadNumber + ".html");
		Post[] posts;
		try (InputStream input = file.openInputStream()) {
			Posts thread = new LocalPostsParser(this, data.threadNumber).convertThread(input);
			if (thread == null) {
				throw new ApiException(ApiException.DELETE_ERROR_NOT_FOUND);
			}
			posts = thread.getPosts();
			if (posts == null || posts.length == 0) {
				throw new ApiException(ApiException.DELETE_ERROR_NOT_FOUND);
			}
		} catch (ParseException e) {
			throw new InvalidResponseException(e);
		} catch (IOException e) {
			throw new ApiException(ApiException.DELETE_ERROR_NOT_FOUND);
		}
		if (!data.postNumbers.get(0).equals(posts[0].getPostNumber())) {
			throw new ApiException(ApiException.DELETE_ERROR_NO_ACCESS);
		}
		removeDirectory(localDownloadDirectory.getChild(data.threadNumber));
		if (!Thread.currentThread().isInterrupted()) {
			file.delete();
		}
		return null;
	}

	private void removeDirectory(DataFile directory) {
		Thread thread = Thread.currentThread();
		List<DataFile> files = directory.getChildren();
		if (files != null) {
			for (DataFile file : files) {
				if (thread.isInterrupted()) {
					return;
				}
				if (file.isDirectory()) {
					removeDirectory(file);
				} else {
					file.delete();
				}
			}
		}
		directory.delete();
	}
}
