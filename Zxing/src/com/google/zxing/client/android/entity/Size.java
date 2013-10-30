/**
 * 
 */
package com.google.zxing.client.android.entity;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @author yinglovezhuzhu@gmail.com
 *
 */
public class Size implements Parcelable {
	
	public int width = 0;
	
	public int height = 0;

	public Size() {}

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public Size(Size src) {
        this.width = src.width;
        this.height = src.height;
    }

    /**
     * Set the size's width and height.
     */
    public void set(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    /**
     * Exchange the width and height value.
     */
    public final void exchange() {
    	int tmp = this.width;
    	this.width = this.height;
    	this.height = tmp;
    }

    /**
     * Returns true if the size's value equal (width,height)
     */
    public final boolean equals(int width, int height) {
        return this.width == width && this.height == height;
    }

    @Override 
    public boolean equals(Object o) {
        if (o instanceof Size) {
            Size p = (Size) o;
            return this.width == p.width && this.height == p.height;
        }
        return false;
    }

    @Override 
    public int hashCode() {
        return width * 32713 + height;
    }

    @Override 
    public String toString() {
        return "Size(" + width + ", " + height + ")";
    }

    /**
     * Parcelable interface methods
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Write this point to the specified parcel. To restore a point from
     * a parcel, use readFromParcel()
     * @param out The parcel to write the point's coordinates into
     */
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(width);
        out.writeInt(height);
    }

    public static final Parcelable.Creator<Size> CREATOR = new Parcelable.Creator<Size>() {
        /**
         * Return a new point from the data in the specified parcel.
         */
        public Size createFromParcel(Parcel in) {
            Size r = new Size();
            r.readFromParcel(in);
            return r;
        }

        /**
         * Return an array of rectangles of the specified size.
         */
        public Size[] newArray(int size) {
            return new Size[size];
        }
    };

    /**
     * Set the point's coordinates from the data stored in the specified
     * parcel. To write a point to a parcel, call writeToParcel().
     *
     * @param in The parcel to read the point's coordinates from
     */
    public void readFromParcel(Parcel in) {
        width = in.readInt();
        height = in.readInt();
    }
}
