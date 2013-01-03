package org.archive.util.binsearch;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.util.iterator.CloseableIterator;

public class SortedTextFile {
	private final static Logger LOGGER =
		Logger.getLogger(SortedTextFile.class.getName());
	
	private SeekableLineReaderFactory factory;
	
	/**
	 * 
	 */
	public SortedTextFile(SeekableLineReaderFactory factory) {
		this.factory = factory;
	}

	public CloseableIterator<String> getRecordIteratorLT(final String prefix) 
	throws IOException {
		return getRecordIterator(prefix, true);
	}

	public CloseableIterator<String> getRecordIterator(final String prefix) 
	throws IOException {
		return getRecordIterator(prefix, false);
	}
	
	public CloseableIterator<String> getRecordIterator(final long offset) throws IOException
	{
		SeekableLineReader slr = factory.get();
		slr.seek(offset);
		return new SeekableLineReaderIterator(slr);
	}

	public CloseableIterator<String> getRecordIterator(final String prefix, 
			boolean lessThan) throws IOException {
		return search(factory.get(),prefix,lessThan);
	}
	
	protected long findOffset(SeekableLineReader slr, final String key) throws IOException
	{
		int blockSize = SeekableLineReaderFactory.BINSEARCH_BLOCK_SIZE;
		long fileSize = slr.getSize();
		long min = 0;
		long max = (long) fileSize / blockSize;
		long mid;
		String line;

		// TODO: implement a cache of midpoints - will make a HUGE difference
		//       on both HTTP and HDFS
	    while (max - min > 1) {
	    	mid = min + (long)((max - min) / 2);
	    	slr.seek(mid * blockSize);
	    	if(mid > 0) line = slr.readLine(); // probably a partial line
	    	line = slr.readLine();
	    	if (key.compareTo(line) > 0) {

	    		if(LOGGER.isLoggable(Level.FINE)) {
	    			LOGGER.fine(String.format("Search(%d) (%s)/(%s) : After",
	    					mid * blockSize, key,line));
	    		}
	    		min = mid;
	    	} else {

	    		if(LOGGER.isLoggable(Level.FINE)) {
	    			LOGGER.fine(String.format("Search(%d) (%s)/(%s) : Before",
					mid * blockSize, key,line));
	    		}
	    		max = mid;
	    	}
	    }
	    // find the right line
	    min = min * blockSize;
	    return min;
	}
	
//	public CloseableIterator<String> getNthSplit(long startOffset, long endOffset, int numSplits, int split)
//	{
//		SeekableLineReader slr = factory.get();
//		long step = (endOffset - startOffset) / numSplits;
//		
//		long start = startOffset + (split * step);
//		long end = start + step;
//		
//		return null;
//	}
	
	public CloseableIterator<String> getSplitIterator(long startOffset, long endOffset, int numSplits) throws IOException
	{
		SeekableLineReader slr = factory.get();
		long step = (endOffset - startOffset) / numSplits;
		
		return new StepSeekingIterator(slr, startOffset, endOffset, step);
	}
	
	public CloseableIterator<String> getSplitIterator(String start, String end, int numSplits) throws IOException
	{
		SeekableLineReader slr = factory.get();
		
		long startOffset = 0;
		
		if (start != null) {
			startOffset = findOffset(slr, start);	
		}
		
		long endOffset = 0;
		
		if (end != null) {
			endOffset = findOffset(slr, end);
		} else {
			endOffset = slr.getSize();
		}
		
		long step = (endOffset - startOffset) / numSplits;
		
		return new StepSeekingIterator(slr, startOffset, endOffset, step);
	}
	
	class StepSeekingIterator implements CloseableIterator<String>
	{
		long currOffset;
		long step;
		long endOffset;
		SeekableLineReader slr;
		
		public StepSeekingIterator(SeekableLineReader slr, long startOffset, long endOffset, long step) throws IOException
		{
			this.slr = slr;
			this.currOffset = startOffset;
			this.step = step;
			this.endOffset = endOffset;
			
			slr.seek(currOffset);
		}

		public boolean hasNext() {
			return (currOffset + 128) < endOffset;
		}

		public String next() {
			
			String line = null;
			
			try {				
				if (currOffset > 0) {
					slr.readLine();
				}
				
				line = slr.readLine();
				
				currOffset += step;
				slr.seek(currOffset);
				
			} catch (IOException io) {
				io.printStackTrace();
			}
			
			return line;
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		public void close() throws IOException {
			slr.close();
		}
	}

	private CloseableIterator<String> search(SeekableLineReader slr,
			final String key, boolean lessThan) throws IOException {

		long min = findOffset(slr, key);

		if(LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(String.format("Aligning(%d)",min));
		}

	    slr.seek(min);
	    
	    String line;
	    
	    if(min > 0) line = slr.readLine();
	    String prev = null;
	    while(true) {
	    	line = slr.readLine();
	    	if(line == null) break;
	    	if(line.compareTo(key) >= 0) break;
	    	prev = line;
	    }
	    if(!lessThan) {
	    	prev = null;
	    }
	    return new CachedStringIterator(slr, prev, line);
	}
	
	public class CachedStringIterator implements CloseableIterator<String> {
		private String first;
		private String second;
		private SeekableLineReader slr;
		private SeekableLineReaderIterator it;
		public CachedStringIterator(SeekableLineReader slr, String first, String second) {
			this.slr = slr;
			this.first = first;
			this.second = second;
			it = new SeekableLineReaderIterator(slr);
		}
		public boolean hasNext() {
			if(first != null) {
				return true;
			}
			if(second != null) {
				return true;
			}
			return it.hasNext();
		}

		public String next() {
			if(first != null) {
				String tmp = first;
				first = null;
				return tmp;
			}
			if(second != null) {
				String tmp = second;
				second = null;
				return tmp;
			}
			
			return it.next();
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		public void close() throws IOException {
			slr.close();
		}
	}
	

}
