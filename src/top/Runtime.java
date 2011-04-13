package top;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public final class Runtime {
	
	public static final String ScheduleMainTaskMethod = Runtime.class.getCanonicalName() + ".scheduleMainTask";
	public static final String ScheduleNormalTaskMethod = Runtime.class.getCanonicalName() + ".scheduleNormalTask";

	public static void scheduleMainTask(Object receiver, String taskName, Object[] args) {
		assert taskName.startsWith(Task.MainTaskMethodPrefix);
		//System.out.println("schedule main task called: " + receiver + "." + taskName + "(" + args + ")");
		Task task = (Task)args[0];
		task.scheduleAsMainTask(receiver, taskName, args);
	}
	
	public static void scheduleNormalTask(Object receiver, String taskName, Object[] args) {
		assert taskName.startsWith(Task.NormalTaskMethodPrefix);
		//System.out.println("schedule normal task called: " + receiver + "." + taskName + "(" + args + ")");
		Task task = (Task)args[0];
		task.scheduleAsNormalTask(receiver, taskName, args);
	}
	
	// volatile array support
	private static Unsafe getUnsafe() {
        Unsafe unsafe = null;
        try {
            Class<?> uc = Unsafe.class;
            Field[] fields = uc.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                if (fields[i].getName().equals("theUnsafe")) {
                    fields[i].setAccessible(true);
                    unsafe = (Unsafe) fields[i].get(uc);
                    break;
                }
            }
        } catch (Exception ex) {
        	ex.printStackTrace();
        }
        return unsafe;
    }
	public static final Unsafe unsafe = getUnsafe();

	public static final int booleanBase = unsafe.arrayBaseOffset(boolean[].class);
    public static final int byteBase = unsafe.arrayBaseOffset(byte[].class);
    public static final int charBase = unsafe.arrayBaseOffset(char[].class);
    public static final int doubleBase = unsafe.arrayBaseOffset(double[].class);
    public static final int floatBase = unsafe.arrayBaseOffset(float[].class);
    public static final int intBase = unsafe.arrayBaseOffset(int[].class);
    public static final int longBase = unsafe.arrayBaseOffset(long[].class);
    public static final int objectBase = unsafe.arrayBaseOffset(Object[].class);
    public static final int shortBase = unsafe.arrayBaseOffset(short[].class);
    
    public static final int booleanScale = unsafe.arrayIndexScale(boolean[].class);
    public static final int byteScale = unsafe.arrayIndexScale(byte[].class);
    public static final int charScale = unsafe.arrayIndexScale(char[].class);
    public static final int doubleScale = unsafe.arrayIndexScale(double[].class);
    public static final int floatScale = unsafe.arrayIndexScale(float[].class);
    public static final int intScale = unsafe.arrayIndexScale(int[].class);
    public static final int longScale = unsafe.arrayIndexScale(long[].class);
    public static final int objectScale = unsafe.arrayIndexScale(Object[].class);
    public static final int shortScale = unsafe.arrayIndexScale(short[].class);
    
    private static long rawIndex(int base, int scale, int i) {
        return base + (long) i * scale;
    }

	public static byte arrayReadByteOrBoolean(Object array, int i) {
		if(array instanceof byte[]) {
			if(i < 0 || i >= ((byte[])array).length)
				throw new IndexOutOfBoundsException("index " + i);
		} else {
			if(i < 0 || i >= ((boolean[])array).length)
				throw new IndexOutOfBoundsException("index " + i);
		}
		
		return unsafe.getByteVolatile(array, rawIndex(byteBase, byteScale, i));
	}
	public static void arrayWriteByteOrBoolean(Object array, int i, byte newValue) {
		if(array instanceof byte[]) {
			if(i < 0 || i >= ((byte[])array).length)
				throw new IndexOutOfBoundsException("index " + i);
		} else {
			if(i < 0 || i >= ((boolean[])array).length)
				throw new IndexOutOfBoundsException("index " + i);
		}
			
		unsafe.putByteVolatile(array, rawIndex(byteBase, byteScale, i), newValue);
	}
	public static char arrayReadChar(Object array, int i) {
		if(i < 0 || i >= ((char[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		return unsafe.getCharVolatile(array, rawIndex(charBase, charScale, i));
	}
	public static void arrayWriteChar(Object array, int i, char newValue) {
		if(i < 0 || i >= ((char[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
		unsafe.putCharVolatile(array, rawIndex(charBase, charScale, i), newValue);
	}
	public static double arrayReadDouble(Object array, int i) {
		if(i < 0 || i >= ((double[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		return unsafe.getDoubleVolatile(array, rawIndex(doubleBase, doubleScale, i));
	}
	public static void arrayWriteDouble(Object array, int i, double newValue) {
		if(i < 0 || i >= ((double[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		unsafe.putDoubleVolatile(array, rawIndex(doubleBase, doubleScale, i), newValue);
	}
	public static float arrayReadFloat(Object array, int i) {
		if(i < 0 || i >= ((float[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		return unsafe.getFloatVolatile(array, rawIndex(floatBase, floatScale, i));
	}
	public static void arrayWriteFloat(Object array, int i, float newValue) {
		if(i < 0 || i >= ((float[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		unsafe.putFloatVolatile(array, rawIndex(floatBase, floatScale, i), newValue);
	}
	public static int arrayReadInt(Object array, int i) {
		if(i < 0 || i >= ((int[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		return unsafe.getIntVolatile(array, rawIndex(intBase, intScale, i));
	}
	public static void arrayWriteInt(Object array, int i, int newValue) {
		if(i < 0 || i >= ((int[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		unsafe.putIntVolatile(array, rawIndex(intBase, intScale, i), newValue);
	}
	public static long arrayReadLong(Object array, int i) {
		if(i < 0 || i >= ((long[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		return unsafe.getLongVolatile(array, rawIndex(longBase, longScale, i));
	}
	public static void arrayWriteLong(Object array, int i, long newValue) {
		if(i < 0 || i >= ((long[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		unsafe.putLongVolatile(array, rawIndex(longBase, longScale, i), newValue);
	}
	public static Object arrayReadObject(Object array, int i) {
		if(i < 0 || i >= ((Object[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		return unsafe.getObjectVolatile(array, rawIndex(objectBase, objectScale, i));
	}
	public static void arrayWriteObject(Object array, int i, Object newValue) {
		if(i < 0 || i >= ((Object[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		unsafe.putObjectVolatile(array, rawIndex(objectBase, objectScale, i), newValue);
	}
	public static short arrayReadShort(Object array, int i) {
		if(i < 0 || i >= ((short[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		return unsafe.getShortVolatile(array, rawIndex(shortBase, shortScale, i));
	}
	public static void arrayWriteShort(Object array, int i, short newValue) {
		if(i < 0 || i >= ((short[])array).length)
			throw new IndexOutOfBoundsException("index " + i);
			
		unsafe.putShortVolatile(array, rawIndex(shortBase, shortScale, i), newValue);
	}
	
}

