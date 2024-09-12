package com.vaguehope.dlnatoad.ui.templates;

import java.util.ArrayList;
import java.util.List;

public class ItemScope {

	public String prevnext_title;
	public String previous_path;
	public String next_path;

	public String item_id;
	public String type;
	public boolean is_img;
	public boolean is_video;
	public boolean is_audio;
	public boolean is_other;

	public String item_path;
	public String item_file_name;
	public String dir_path;
	public String dir_name;

	public final List<Tag> tags = new ArrayList<>();
	public boolean edit_tags;
	public String tags_edit_path;
	public String tags_post_path;
	public boolean autofocus_add_tag;

	public String details;

	public void addTag(final String tag, final String cls, final String search_path, final String b64) {
		this.tags.add(new Tag(tag, cls, search_path, b64));
	}

	public static class Tag {
		public final String tag;
		public final String cls;
		public final String search_path;
		public final String b64;

		Tag(final String tag, final String cls, final String search_path, final String b64) {
			this.tag = tag;
			this.cls = cls;
			this.search_path = search_path;
			this.b64 = b64;
		}
	}
}
