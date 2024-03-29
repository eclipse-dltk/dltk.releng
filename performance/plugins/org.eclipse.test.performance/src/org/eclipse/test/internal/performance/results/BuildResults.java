/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.test.internal.performance.results;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.StringTokenizer;

import org.eclipse.test.internal.performance.InternalPerformanceMeter;
import org.eclipse.test.internal.performance.data.Dim;

/**
 * Class providing numbers of a scenario running on a specific configuration
 * at a specific time (for example 'I20070615-1200').
 */
public class BuildResults extends AbstractResults {

	// Build information
	String date;
	String comment;
	int summaryKind = -1;

	// Dimensions information
	Dim[] dimensions;
	double[] average, stddev;
	long[] count;
	double[][] values;

	// Comparison information
	boolean baseline;
	String failure;

BuildResults(AbstractResults parent) {
	super(parent, -1);
}

BuildResults(AbstractResults parent, int id) {
	super(parent, id);
	this.name = DB_Results.getBuildName(id);
	this.baseline = this.name.startsWith(AbstractResults.VERSION_REF);
}

/*
 * Clean values when several measures has been done for the same build.
 */
void cleanValues() {
	int length = values.length;
	for (int dim_id=0; dim_id<length; dim_id++) {
		int vLength = values[dim_id].length;
		/* Log clean operation
		if (dim_id == 0) {
			IStatus status = new Status(IStatus.WARNING, PerformanceTestPlugin.PLUGIN_ID, "Clean "+vLength+" values for "+this.parent+">"+this.name+" ("+this.count[dim_id]+" measures)...");    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$ //$NON-NLS-5$
			PerformanceTestPlugin.log(status);
		}
		*/
		this.average[dim_id] = 0;
		for (int i=0; i<vLength; i++) {
			this.average[dim_id] += values[dim_id][i];
		}
		this.average[dim_id] /= vLength;
		double squaredDeviations= 0;
		for (int i=0; i<vLength; i++) {
		    double deviation= this.average[dim_id] - values[dim_id][i];
		    squaredDeviations += deviation * deviation;
		}
		this.stddev[dim_id] = Math.sqrt(squaredDeviations / (this.count[dim_id] - 1)); // unbiased sample stdev
		this.values[dim_id] = null;
	}
	for (int i=0; i<length; i++) {
		if (this.values[i] != null) {
			return;
		}
	}
	this.values = null;
}

/**
 * Compare build results using the date of the build.
 * 
 * @see Comparable#compareTo(Object)
 */
public int compareTo(Object obj) {
	if (obj instanceof BuildResults) {
		BuildResults res = (BuildResults) obj;
		return getDate().compareTo(res.getDate());
	}
	return -1;
}

/**
 * Returns the comment associated with the scenario for the current build.
 *
 * @return The comment associated with the scenario for the current build
 * 	or <code>null</code> if no comment was stored for it.
 */
public String getComment() {
	if (this.comment == null) {
		return null;
	}
	return this.comment;
}

/**
 * Return the number of stored values for the default dimension
 * 
 * @return the number of stored values for the default dimension
 */
public long getCount() {
	return this.count[DEFAULT_DIM_INDEX];
}

/*
 * Return the number of stored values for the given dimension
 *  (see {@link Dim#getId()})
 */
long getCount(int dim_id) {
	return this.count[getDimIndex(dim_id)];
}

/**
 * Returns the date of the build which is a part of its name.
 *
 * @return The date of the build as yyyyMMddHHmm
 */
public String getDate() {
	if (this.date == null) {
		if (this.baseline) {
			int length = this.name.length();
			this.date = this.name.substring(length-12, length);
		} else {
			char first = this.name.charAt(0);
			if (first == 'N' || first == 'I' || first == 'M') { // TODO (frederic) should be buildIdPrefixes...
				if (this.name.length() == 14) {
					this.date = this.name.substring(1, 9)+this.name.substring(10, 14);
				} else {
					this.date = this.name.substring(1);
				}
			} else {
				int length = this.name.length() - 12 /* length of date */;
				for (int i=0; i<=length; i++) {
					try {
						String substring = i == 0 ? this.name : this.name.substring(i);
						DATE_FORMAT.parse(substring);
						this.date = substring; // if no exception is raised then the substring has a correct date format => store it
						break;
					} catch(ParseException ex) {
						// skip
					}
				}
			}
		}
	}
	return this.date;
}

/**
 * Returns the standard deviation of the default dimension computed
 * while running the scenario for the current build.
 * 
 * @return The value of the standard deviation
 */
public double getDeviation() {
	return this.stddev[DEFAULT_DIM_INDEX];
}

/**
 * Returns the standard deviation of the given dimension computed
 * while running the scenario for the current build.
 * 
 * @param dim_id The id of the dimension (see {@link Dim#getId()})
 * @return The value of the standard deviation
 */
public double getDeviation(int dim_id) {
	return this.stddev[getDimIndex(dim_id)];
}

/*
 * Return the index of the dimension corresponding to the given
 * dimension id (see {@link Dim#getId()})
 */
int getDimIndex(int dim_id) {
	if (this.dimensions == null) return -1;
	int length = this.dimensions.length;
	for (int i=0; i<length; i++) {
		if (this.dimensions[i] == null) break;
		if (this.dimensions[i].getId() == dim_id) {
			return i;
		}
	}
	return -1;
}

/**
 * Return the error computed while storing values for the default dimension
 * 
 * @return the error of the measures stored for the default dimension
 */
public double getError() {
	long n = getCount();
	if (n == 1) return Double.NaN;
	return getDeviation() / Math.sqrt(n);
}

/*
 * Return the error computed while storing values for the given dimension
 *  (see {@link Dim#getId()})
 */
double getError(int dim_id) {
	long n = getCount(dim_id);
	if (n == 1) return Double.NaN;
	return getDeviation(dim_id) / Math.sqrt(n);
}

/**
 * Return the failure message which may happened on this scenario
 * while running the current build
 *
 * @return The failure message or <code>null</null> if the scenario passed.
 */
public String getFailure() {
	return this.failure;
}

/**
 * Return the value of the performance result stored
 * for the given dimension of the current build.
 * 
 * @param dim_id The id of the dimension (see {@link Dim#getId()})
 * @return The value of the performance result
 */
public double getValue(int dim_id) {
	return this.average[getDimIndex(dim_id)];
}

/**
 * Return the value of the performance result stored
 * for the default dimension of the current build.
 * 
 * @return The value of the performance result
 */
public double getValue() {
	return this.average[DEFAULT_DIM_INDEX];
}

/**
 * Returns whether the build is a baseline build or not.
 * 
 * @return <code>true</code> if the build name starts with the baseline prefix
 * 	(see {@link PerformanceResults#getBaselinePrefix()} or <code>false</code>
 * 	otherwise.
 */
public boolean isBaseline() {
	return baseline;
}

/**
 * Returns whether the build has a summary or not. Note that the summary
 * may be global or not.
 * 
 * @return <code>true</code> if the summary kind is equals to 0 or 1
 * 	<code>false</code> otherwise.
 */
public boolean hasSummary() {
	return this.summaryKind >= 0;
}
/**
 * Returns whether the build has a global summary or not.
 * 
 * @return <code>true</code> if the summary kind is equals to 1
 * 	<code>false</code> otherwise.
 */
public boolean hasGlobalSummary() {
	return this.summaryKind == 1;
}

/*
 * Returns a given pattern match the build name or not.
 */
boolean match(String pattern) {
	if (pattern.equals("*")) return true; //$NON-NLS-1$
	if (pattern.indexOf('*') < 0 && pattern.indexOf('?') < 0) {
		pattern += "*"; //$NON-NLS-1$
	}
	StringTokenizer tokenizer = new StringTokenizer(pattern, "*?", true); //$NON-NLS-1$
	int start = 0;
	String previous = ""; //$NON-NLS-1$
	while (tokenizer.hasMoreTokens()) {
		String token = tokenizer.nextToken();
		if (!token.equals("*") && !token.equals("?")) { //$NON-NLS-1$ //$NON-NLS-2$
			if (previous.equals("*")) { //$NON-NLS-1$
				int idx = this.name.substring(start).indexOf(token);
				if (idx < 0) return false;
				start += idx;
			} else {
				if (previous.equals("?")) start++; //$NON-NLS-1$
				if (!name.substring(start).startsWith(token)) return false;
			}
			start += token.length();
		}
		previous = token;
	}
	if (previous.equals("*")) { //$NON-NLS-1$
		return true;
	} else if (previous.equals("?")) { //$NON-NLS-1$
		return this.name.length() == start;
	}
	return this.name.endsWith(previous);
}

/*
 * Read the build results data from the given stream.
 */
void readData(DataInputStream stream, int version) throws IOException {
	long timeBuild = stream.readLong();
	this.date = new Long(timeBuild).toString();
	byte kind = stream.readByte();
	this.baseline = kind == 0;
	if (this.baseline) {
		this.name = getPerformance().baselinePrefix + '_' + this.date;
	} else {
		String suffix = this.date.substring(0, 8) + '-' + this.date.substring(8);
		switch (kind) {
			case 1:
				this.name = "N" + suffix; //$NON-NLS-1$
				break;
			case 2:
				this.name = "I" + suffix; //$NON-NLS-1$
				break;
			case 3:
				this.name = "M" + suffix; //$NON-NLS-1$
				break;
			default:
				this.name = stream.readUTF();
				break;
		}
	}
	int length = stream.readInt();
	this.dimensions = new Dim[length];
	this.average = new double[length];
	this.stddev = new double[length];
	this.count = new long[length];
	for (int i=0; i<length; i++) {
		this.dimensions[i] = getDimension(stream.readInt());
		this.average[i] = stream.readLong();
		this.count[i] = stream.readLong();
		this.stddev[i] = stream.readDouble();
	}
	this.id = DB_Results.getBuildId(this.name);
	switch (version) {
		case 0:
			// no other information were stored in version 0
			break;
		default:
			// extra infos (summary, failure and comment) are also stored in local data files
			this.summaryKind = stream.readInt();
			String str = stream.readUTF();
			if (str.length() > 0) this.failure = str;
			str = stream.readUTF();
			if (str.length() > 0) this.comment = str;
			break;
	}
}

/*
 * Set the build summary and its associated comment.
 */
void setComment(String comment) {
	if (comment != null && this.comment == null) {
		this.comment = comment;
	}
}

/*
 * Set the build summary and its associated comment.
 */
void setSummary(int kind, String comment) {
	this.comment = comment;
	this.summaryKind = kind;
}

/*
 * Set the build failure.
 */
void setFailure(String failure) {
	this.failure = failure;
}

/*
 * Set the build value from database information.
 */
void setValue(int dim_id, int step, long value) {
	int length = SUPPORTED_DIMS.length;
	Dim dimension = getDimension(dim_id);
	int idx = 0;
	if (this.dimensions == null){
		this.dimensions = new Dim[length];
		this.average = new double[length];
		this.stddev = new double[length];
		this.count = new long[length];
		this.dimensions[0] = dimension;
		for (int i=0; i<length; i++) {
			// init average numbers with an impossible value
			// to clearly identify whether it's already set or not
			// when several measures are made for the same build
			this.average[i] = -1;
		}
	} else {
		length = this.dimensions.length;
		for (int i=0; i<length; i++) {
			if (this.dimensions[i] == null) {
				this.dimensions[i] = dimension;
				idx = i;
				break;
			}
			if (this.dimensions[i].getId() == dim_id) {
				idx = i;
				break;
			}
		}
	}
	switch (step) {
		case InternalPerformanceMeter.AVERAGE:
			if (this.average[idx] != -1) {
				if (values == null) {
					values = new double[length][];
					values[idx] = new double[2];
					values[idx][0] = this.average[idx];
					values[idx][1] = value;
					this.average[idx] = -1;
				} else if (this.values[idx] == null) {
					values[idx] = new double[2];
					values[idx][0] = this.average[idx];
					values[idx][1] = value;
					this.average[idx] = -1;
				}
			} else if (this.values != null && this.values[idx] != null) {
				int vLength = values[idx].length;
				System.arraycopy(values[idx], 0, values[idx] = new double[vLength+1], 0, vLength);
				values[idx][vLength] = value;
			} else {
				this.average[idx] = value;
			}
			break;
		case InternalPerformanceMeter.STDEV:
			this.stddev[idx] += Double.longBitsToDouble(value);
			break;
		case InternalPerformanceMeter.SIZE:
			this.count[idx] += value;
			break;
	}
}

/* (non-Javadoc)
 * @see org.eclipse.test.internal.performance.results.AbstractResults#toString()
 */
public String toString() {
	StringBuffer buffer = new StringBuffer(this.name);
	buffer.append(": "); //$NON-NLS-1$
	int length = this.dimensions.length;
	for (int i=0; i<length; i++) {
		if (i>0)	buffer.append(", "); //$NON-NLS-1$
		buffer.append('[');
		buffer.append(dimensions[i].getId());
		buffer.append("]="); //$NON-NLS-1$
		buffer.append(average[i]);
		buffer.append('/');
		buffer.append(count[i]);
		buffer.append('/');
		buffer.append(Math.round(stddev[i]*1000)/1000.0);
	}
	return buffer.toString();
}

/*
 * Write the build results data in the given stream.
 */
void write(DataOutputStream stream) throws IOException {
	long timeBuild = -1;
    try {
	    timeBuild = Long.parseLong(getDate());
    } catch (NumberFormatException nfe) {
	    // do nothing
    	nfe.printStackTrace();
    }
	stream.writeLong(timeBuild);
	byte kind = 0; // baseline
	if (!this.baseline) {
		switch (this.name.charAt(0)) {
			case 'N':
				kind = 1;
				break;
			case 'I':
				kind = 2;
				break;
			case 'M':
				kind = 3;
				break;
			default:
				kind = 4;
				break;
		}
	}
	stream.writeByte(kind);
	if (kind == 4) {
		stream.writeUTF(this.name);
	}
	int length = this.dimensions == null ? 0 : this.dimensions.length;
	stream.writeInt(length);
	for (int i=0; i<length; i++) {
		stream.writeInt(this.dimensions[i].getId());
		stream.writeLong((long)this.average[i]) ;
		stream.writeLong(this.count[i]);
		stream.writeDouble(this.stddev[i]);
	}

	// Write extra infos (summary, failure and comment)
	stream.writeInt(this.summaryKind);
	stream.writeUTF(this.failure == null ? "" : this.failure) ; //$NON-NLS-1$
	stream.writeUTF(this.comment == null ? "" : this.comment) ; //$NON-NLS-1$
}

}
