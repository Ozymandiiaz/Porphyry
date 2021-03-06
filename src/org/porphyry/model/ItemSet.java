/*
PORPHYRY - Digital space for confronting interpretations about documents

OFFICIAL WEB SITE
http://porphyry.sf.net/

Copyright (C) 2007, 2011 Aurelien Benel.

LEGAL ISSUES
This program is free software; you can redistribute it and/or modify it under 
the terms of the GNU General Public License as published by the Free Software 
Foundation, either version 3 of the license, or (at your option) any later 
version.
This program is distributed in the hope that it will be useful, but WITHOUT ANY 
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
PARTICULAR PURPOSE. See the GNU General Public License for more details: 
http://www.gnu.org/licenses/gpl.html
*/

package org.porphyry.model;

import org.hypertopic.*;
import org.json.JSONArray;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.Rectangle;

/**
 * Set of items containing highlights.
 * Generalizes addAll and retainAll methods to handle those two levels
 * and partial highlights intersections.
 */
public class ItemSet {//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

private final Map<String,Item> items = new HashMap();

public void addAll(ItemSet that) {
	for (Item i : that.items.values()) {
		Item old = this.items.get(i.getID());
		if (old==null) {
			this.items.put(i.getID(), i);
		} else {
			old.join(i);
		}
	}
}

public void retainAll(ItemSet that) {
	Iterator <Map.Entry<String,Item>> i = 
		this.items.entrySet().iterator();
	while (i.hasNext()) {
		Map.Entry<String,Item> thisEntry = i.next();
		Item thatItem = that.items.get(thisEntry.getKey());
		if (thatItem==null) {
			i.remove();
		} else {
			thisEntry.getValue().retainAll(thatItem);
		}
	}
}

public int countItems() {
	return this.items.size();
}

public int countHighlights() {
	int count = 0;
	for (Item i : this.items.values()) {
		count+=i.size();
	}
	return count;
}

public ItemSet() {
}

public ItemSet(Collection<HypertopicMap.Corpus.Item> items) throws Exception {
	for (HypertopicMap.Corpus.Item i : items) {
		this.items.put(i.getID(), new Item(i));
	}
}

public ItemSet(HypertopicMap.Corpus corpus) throws Exception {
	this(corpus.getItems());
}

public ItemSet(
	Collection<HypertopicMap.Corpus.Item> items,
	Collection<HypertopicMap.Corpus.Item.Highlight> highlights
) throws Exception {
	this(items);
	for (HypertopicMap.Corpus.Item.Highlight h : highlights) {
		this.add(h);
	}
}

public ItemSet(HypertopicMap.Viewpoint.Topic topic) throws Exception {
	this(topic.getItems(), topic.getHighlights());
}

protected void add(HypertopicMap.Corpus.Item.Highlight highlight) 
	throws Exception 
{
	HypertopicMap.Corpus.Item parent = highlight.getItem();
	Item inside = this.items.get(parent.getID());
	if (inside==null) {
		inside = new Item(parent);
		this.items.put(inside.getID(), inside);
	}
	inside.add(highlight);
}

//TODO without highlights to save memory?
public Collection<Item> getItems() {
	return this.items.values();
}

//TODO pattern sort for KWIC?
public Collection<Item.Highlight> getHighlights() {
	Collection<Item.Highlight> result = new ArrayList();
	for (Item i : this.items.values()) {
		result.addAll(i.getHighlights());
	}
	return result;
}

@Override public String toString() {
	String s = "";
	for (Item i: this.items.values()) {
		s += i;
	}
	return s;
}

public class Item {//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

private final String id;
private final String name;
private final URL resource;
private final URL thumbnail;
private final Set<Highlight> highlights = Collections.newSetFromMap(
	new ConcurrentHashMap()
);

public Item(String id, String name, URL resource, URL thumbnail) {
	this.id = id;
	this.name = name;
  this.resource = resource;
  this.thumbnail = thumbnail;
}

public Item(HypertopicMap.Corpus.Item item) throws Exception {
	this(item.getID(), item.getName(), item.getResource(), item.getThumbnail());
}

public String getID() {
	return this.id;
}

public String getName() {
	return this.name;
}

public URL getResource() {
	return this.resource;
}

public URL getThumbnail() {
	return this.thumbnail;
}

public int size() {
	return this.highlights.size();
}

public void add(HypertopicMap.Corpus.Item.Highlight highlight) 
	throws Exception 
{
	JSONArray coord = highlight.getCoordinates();
	int size = coord.length();
	Highlight h;
	switch (size) {
		case 2: 
			h = new TextHighlight(
				coord.getInt(0),
				coord.getInt(1),
				highlight.getText()
			);
			break;
		case 4: 
			h = new PictureHighlight(
				coord.getInt(0),
				coord.getInt(1),
				coord.getInt(2),
				coord.getInt(3)
			);
			break;
		default: throw new Exception(
			"Highlights with " + size + " dimensions not supported."
		);
	}
	this.add(h);
}

public void add(Highlight highlight) {
	Iterator<Highlight> h = this.highlights.iterator();
	while (h.hasNext()) {
		Highlight old = h.next();
		if (old.intersects(highlight)) {
			highlight.join(old);
			h.remove();
		}
	}
	this.highlights.add(highlight);
}

public void join(Item that) {
	for (Highlight h : that.highlights) {
		this.add(h);
	}
}

// TODO check if the need for a concurrent hashset is not due to an overcomplex
// algorithm.
public void retainAll(Item that) {
	Iterator<Highlight> h = this.highlights.iterator();
	while (h.hasNext()) {
		Highlight thisHighlight = h.next();
		h.remove();
		boolean found = false;
		for (Highlight thatHighlight : that.highlights) {
			if (thatHighlight.intersects(thisHighlight)) {
				thisHighlight.join(thatHighlight);
				found = true;
			}
		}
		if (found) {
			this.add(thisHighlight);
		}
	}
}

//TODO safer?
public Collection<Highlight> getHighlights() {
	return this.highlights;
}

@Override public boolean equals(Object that) {
	return that instanceof Item
		&& this.id.equals(((Item) that).id);
}

@Override public String toString() {
	String s = "==" + this.name + "==\n";
	for (Highlight h: this.getHighlights()) {
		s += h + "\n";
	}
	return s;
}

/**
 * Highlights stored in the server or computed from joins.
 */
public abstract class Highlight {//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

public Item getItem() {
	return Item.this;
}

abstract public URL getResource() throws Exception;

abstract public boolean intersects(Highlight that);

abstract public void join(Highlight that);

}//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Highlight

class TextHighlight extends Highlight {//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

private int begin, end;
private Collection<String> texts;

public TextHighlight(int begin, int end, Collection<String> texts) {
	this.begin = begin;
	this.end = end;
	this.texts = texts;
}

/**
* See RFC 5147.
*/
@Override public URL getResource() throws Exception {
  return new URL(
    Item.this.getResource().toString() + "#char=" + begin + "," + end
  );
}

@Override public boolean intersects(Highlight highlight) {
	if (!(
		highlight instanceof TextHighlight 
		&& Item.this.equals(highlight.getItem())
	)) {
		return false;
	}
	TextHighlight that = (TextHighlight) highlight;
	return this.begin<=that.begin && that.begin<this.end
		|| this.begin<that.end && that.end<=this.end
		|| that.begin<=this.begin && this.begin<that.end;
}

@Override public void join(Highlight highlight) {
	TextHighlight that = (TextHighlight) highlight;
	this.begin = Math.min(this.begin, that.begin);
	this.end = Math.max(this.end, that.end);
	this.texts.addAll(that.texts);
}

@Override public String toString() {
	String result = "<html><pre>";
	Iterator<String> i = this.texts.iterator();
  while (i.hasNext()) {
    result += "</pre><pre>" + i.next();
  }
  result += "</pre>";
	return result.replace("\n"," ");
}

}//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< TextHighlight

class PictureHighlight extends Highlight {//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

private Rectangle rectangle;

public PictureHighlight(Rectangle rectangle) {
	this.rectangle = rectangle.getBounds();
}

public PictureHighlight(int x1, int y1, int x2, int y2) {
	this.rectangle = new Rectangle(x1, y1, x2-x1, y2-y1);
}

/**
* See W3C Media Fragments URI.
*/
@Override public URL getResource() throws Exception {
  return new URL(
    Item.this.getResource().toString() + "#xywh=" 
      + this.rectangle.x + "," 
      + this.rectangle.y + "," 
      + this.rectangle.width + "," 
      + this.rectangle.height
  );
}

//TODO getThumbnail 

@Override public boolean intersects(Highlight that) {
	return that instanceof PictureHighlight 
		&& Item.this.equals(that.getItem())
		&& this.rectangle.intersects(
			((PictureHighlight) that).rectangle
		);
}

@Override public void join(Highlight that) {
	this.rectangle = this.rectangle.union(
		((PictureHighlight) that).rectangle
	);
}

}//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< PictureHighlight

}//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Item

}//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< ItemSet
