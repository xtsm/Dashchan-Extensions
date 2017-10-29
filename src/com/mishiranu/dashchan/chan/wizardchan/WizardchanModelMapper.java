package com.mishiranu.dashchan.chan.wizardchan;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.Uri;

import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class WizardchanModelMapper {
	public static FileAttachment createFileAttachment(JSONObject jsonObject, ChanLocator locator, String boardName)
			throws JSONException {
		FileAttachment attachment = new FileAttachment();
		String tim = CommonUtils.getJsonString(jsonObject, "tim");
		String filename = CommonUtils.getJsonString(jsonObject, "filename");
		String ext = CommonUtils.getJsonString(jsonObject, "ext");
		attachment.setSize(jsonObject.optInt("fsize"));
		attachment.setWidth(jsonObject.optInt("w"));
		attachment.setHeight(jsonObject.optInt("h"));
		attachment.setFileUri(locator, locator.buildPath(boardName, "src", tim + ext));
		attachment.setThumbnailUri(locator, locator.buildPath(boardName, "thumb", tim + ".gif"));
		attachment.setOriginalName(filename);
		return attachment;
	}

	public static Post createPost(JSONObject jsonObject, ChanLocator locator, String boardName) throws JSONException {
		Post post = new Post();
		if (jsonObject.optInt("sticky") != 0) {
			post.setSticky(true);
		}
		if (jsonObject.optInt("locked") != 0) {
			post.setClosed(true);
		}
		String no = CommonUtils.getJsonString(jsonObject, "no");
		String resto = CommonUtils.getJsonString(jsonObject, "resto");
		post.setPostNumber(no);
		if (!"0".equals(resto)) {
			post.setParentPostNumber(resto);
		}
		post.setTimestamp(jsonObject.getLong("time") * 1000L);
		String name = CommonUtils.optJsonString(jsonObject, "name");
		if (name != null) {
			name = StringUtils.nullIfEmpty(StringUtils.clearHtml(name).trim());
			post.setName(name);
		}
		post.setTripcode(CommonUtils.optJsonString(jsonObject, "trip"));
		post.setIdentifier(CommonUtils.optJsonString(jsonObject, "id"));
		post.setCapcode(CommonUtils.optJsonString(jsonObject, "capcode"));
		String email = CommonUtils.optJsonString(jsonObject, "email");
		if (!StringUtils.isEmpty(email) && email.equalsIgnoreCase("sage")) {
			post.setSage(true);
		} else {
			try {
				post.setEmail(URLDecoder.decode(StringUtils.clearHtml(email), "UTF-8").trim());
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException(e);
			}
		}
		String country = CommonUtils.optJsonString(jsonObject, "country");
		String countryName = CommonUtils.optJsonString(jsonObject, "country_name");
		if (country != null) {
			Uri uri = locator.buildPath("static", "flags", country.toLowerCase(Locale.US) + ".png");
			String title = countryName == null ? country.toUpperCase(Locale.US) : countryName;
			post.setIcons(new Icon(locator, uri, title));
		}
		String sub = CommonUtils.optJsonString(jsonObject, "sub");
		if (sub != null) {
			sub = StringUtils.nullIfEmpty(StringUtils.clearHtml(sub).trim());
			post.setSubject(sub);
		}
		String com = CommonUtils.optJsonString(jsonObject, "com");
		if (com != null) {
			// Vichan JSON API bug, sometimes comment is broken
			com = com.replace("<a  ", "<a ").replaceAll("href=\"\\?", "href=\"");
			post.setComment(com);
		}
		try {
			post.setAttachments(createFileAttachment(jsonObject, locator, boardName));
		} catch (JSONException e) {
			// Ignore exception
		}
		return post;
	}

	public static Posts createThread(JSONObject jsonObject, ChanLocator locator, String boardName,
			boolean fromCatalog) throws JSONException {
		Post[] posts;
		int postsCount = 0;
		int filesCount = 0;
		if (fromCatalog) {
			Post post = createPost(jsonObject, locator, boardName);
			postsCount = jsonObject.getInt("replies") + 1;
			filesCount = jsonObject.getInt("omitted_images") + jsonObject.getInt("images");
			filesCount += post.getAttachmentsCount();
			posts = new Post[] {post};
		} else {
			JSONArray jsonArray = jsonObject.getJSONArray("posts");
			posts = new Post[jsonArray.length()];
			for (int i = 0; i < posts.length; i++) {
				jsonObject = jsonArray.getJSONObject(i);
				posts[i] = createPost(jsonObject, locator, boardName);
				if (i == 0) {
					postsCount = jsonObject.getInt("replies") + 1;
					filesCount = jsonObject.getInt("omitted_images") + jsonObject.getInt("images");
					filesCount += posts[0].getAttachmentsCount();
				}
			}
		}
		return new Posts(posts).addPostsCount(postsCount).addFilesCount(filesCount);
	}
}
