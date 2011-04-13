package top;

import java.util.HashSet;
import java.util.Set;

import top.utils.ConcurrentIdentityHashMap;

/**
 * the KeyChain does not leak internal objects such as the Keys. Therefore, any Object
 * in the public API is either a Task (depending on the KeyChain implementation, e.g., a Thread or an Interval)
 * or a normal Object (that again has an associated key chain)
 * @author angererc
 *
 */
public abstract class Permissions<T> {

	/**
	 * returns the global instance of the KeyChain; for now we only support Threads
	 * but that may change
	 */
	public static final Permissions<Task> perm = new Permissions<Task>() {
		@Override
		public Task now() {
			return Task.now();
		}
	};
	
	/*
	 * Exceptions
	 */
	
	public static class KeyChainException extends RuntimeException {
		KeyChainException(String message) {
			super(message);
		}		
	}
	
	private KeyChainException newAddKeyException(T now, KeyChain keyChain, T key) {
		return new KeyChainException("AddKeyException: " + now + " cannot add key " + key + "; no read access; KeyChain=" + keyChain);
	}
	
	private KeyChainException newReplaceKeyException(T now, KeyChain keyChain, T with) {
		return new KeyChainException("ReplaceKeyException: Cannot replace now " + now + " with " + with + "; no read access; KeyChain=" + keyChain);
	}
	
	private KeyChainException newLinkKeychainsException(T now, KeyChain masterKey, KeyChain slaveKey) {
		return new KeyChainException("LinkKeychainsException: Cannot link slave key chain " + slaveKey + " to master key chain " + masterKey + " in now " + now + "; now does not own the slave key");
	}
	
	private KeyChainException newUnlinkKeychainsException(T now, KeyChain slaveKey) {
		return new KeyChainException("UnlinkKeychainsException: Cannot unlink slave key chain " + slaveKey + " in now " + now + "; now does not own the slave key");
	}
	
	private KeyChainException newReadAccessException(T now, KeyChain key, Object o) {
		return new KeyChainException("ReadAccessException: " + now + " cannot read " + o + "; KeyChain=" + key);
	}
	
	private KeyChainException newWriteAccessException(T now, KeyChain key, Object o) {
		return new KeyChainException("WriteAccessException: " + now + " cannot write " + o + "; KeyChain=" + key);
	}
		
	/*
	 * KeyChains
	 */
	abstract class KeyChain {
		/**
		 * checkRead assumes an acyclic keyChain graph; that is, if o1 in transitive KeyChain(o2) then o2 not in transitive key chain of o1 
		 * @param now
		 * @param o
		 */
		abstract void checkRead(T now, Object o);		
		abstract void checkWrite(T now, Object o);
		//the following two must atomically call checkRead()
		abstract KeyChain add(T now, T key); //in now, add key; now must be able to read
		abstract KeyChain replace(T now, T with);
		abstract boolean isOwnedBy(T now); //true only if key chain is a single key chain and now is in it
		abstract boolean isImmutable();
		abstract boolean isShared();
	}
	
	private class SingleKeyChain extends KeyChain {
		final T task;
		SingleKeyChain(T task) {
			this.task = task;
		}
		
		@Override
		boolean isOwnedBy(T now) {
			return now == task;
		}
		
		@Override
		void checkRead(T now, Object o) {
			if(now != task) {
				throw newReadAccessException(now, this, o);
			}
		}
		
		@Override
		void checkWrite(T now, Object o) {
			if(now != task) {
				throw newWriteAccessException(now, this, o);
			}
		}

		@Override
		KeyChain add(T now, T key) {
			if(now != task) {
				throw newAddKeyException(now, this, key);
			}
			if(key == this.task) {
				return this;
			} else  {
				return new MultiKeyChain(this.task, key);
			}
		}

		@Override
		KeyChain replace(T now, T with) {
			if(now != task) {
				throw newReplaceKeyException(now, this, with);
			}
			return new SingleKeyChain(with);
		}
		
		@Override
		public boolean isImmutable() {
			return false;
		}
		
		@Override
		public boolean isShared() {
			return false;
		}
		
		@Override
		public String toString() {
			return "SingleKeyChain(" + task + ")";
		}
	}
	
	private class MultiKeyChain extends KeyChain {
		private Set<T> tasks;
		MultiKeyChain(T one, T other) {
			assert one != other;
			tasks = new HashSet<T>();
			tasks.add(one);
			tasks.add(other);
		}
		
		@Override
		boolean isOwnedBy(T now) {
			return false;
		}
		
		@Override
		public synchronized void checkRead(T now, Object o) {
			if(! tasks.contains(now))
				throw newReadAccessException(now, this, o);
		}
		
		@Override
		public synchronized void checkWrite(T now, Object o) {
			throw newWriteAccessException(now, this, o);
		}
		
		@Override
		public synchronized KeyChain add(T now, T key) {
			if(! tasks.contains(now)) {
				throw newAddKeyException(now, this, key);
			}
			tasks.add(key);
			return this;			
		}
		
		@Override
		public synchronized KeyChain replace(T now, T with) {
			if(! tasks.contains(now)) {
				throw newReplaceKeyException(now, this, with);
			}

			//invariant is that tasks.size >= 2
			assert tasks.size() >= 2;
			
			if(tasks.size() == 2 && tasks.contains(with)) {
				//if we would remove now we'd be only left with 'with'
				return new SingleKeyChain(with);				
			} else {
				//size() > 2 or with is not in the set yet
				tasks.remove(now);
				tasks.add(with);
				return this;
			}
		}
		
		@Override
		public boolean isImmutable() {
			return false;
		}
		
		@Override
		public boolean isShared() {
			return false;
		}
		
		@Override
		public String toString() {
			return "MultiKeyChain(" + tasks + ")";
		}
	}
	
	private class DelegatingKeyChain extends KeyChain {
		final Object target;
		
		DelegatingKeyChain(Object o) {
			this.target = o;
		}
		@Override
		KeyChain add(T now, T key) {
			throw newAddKeyException(now, this, key);
		}

		@Override
		void checkRead(T now, Object o) {
			getOrCreateKeyChain(now, target).checkRead(now, o);
		}

		@Override
		void checkWrite(T now, Object o) {
			getOrCreateKeyChain(now, target).checkWrite(now, o);
		}

		@Override
		boolean isOwnedBy(T now) {
			return getOrCreateKeyChain(now, target).isOwnedBy(now);
		}

		@Override
		KeyChain replace(T now, T with) {
			throw newReplaceKeyException(now, this, with);
		}
		
		@Override
		public boolean isImmutable() {
			return getOrCreateKeyChain(now(), target).isImmutable();
		}
		
		@Override
		public boolean isShared() {
			return getOrCreateKeyChain(now(), target).isShared();
		}
		
		@Override
		public String toString() {
			return "DelegatingKeyChain(" + target + "->" + keyChain.get(target) + ")";
		}
	}
	
	
	/*
	 * Permissions API
	 */
	
	private final ConcurrentIdentityHashMap<Object, KeyChain> keyChain = new ConcurrentIdentityHashMap<Object, KeyChain>();
	private final KeyChain immutableKey = new KeyChain() {

		@Override
		boolean isOwnedBy(T now) {
			return false;
		}
		
		@Override
		KeyChain add(T now, T key) {
			return this;
		}

		@Override
		KeyChain replace(T key, T with) {
			return this;
		}

		@Override
		public void checkRead(T now, Object o) {	
			//read on immutable is always OK
		}

		@Override
		public void checkWrite(T now, Object o) {
			//never write
			throw newWriteAccessException(now, this, o);
		}
		
		@Override
		public boolean isImmutable() {
			return true;
		}
		
		@Override
		public boolean isShared() {
			return false;
		}
		
		@Override
		public String toString() {
			return "ImmutableKey";
		}
		
	};
	private final KeyChain sharedKey = new KeyChain() {

		@Override
		boolean isOwnedBy(T now) {
			return false;
		}
		
		@Override
		KeyChain add(T now, T key) {
			return this;
		}

		@Override
		KeyChain replace(T key, T with) {
			return this;
		}

		@Override
		public void checkRead(T now, Object o) {
			//read always OK
		}

		@Override
		public void checkWrite(T now, Object o) {
			//write always OK
		}
		
		@Override
		public boolean isImmutable() {
			return false;
		}
		
		@Override
		public boolean isShared() {
			return true;
		}
		
		@Override
		public String toString() {
			return "SharedKey";
		}
		
	};
	
	protected Permissions() {
		
	}
	
	public abstract T now();
	
	/**
	 * throws an access exception if now() cannot read object o
	 * now can read o if now has been added as a key to o or if somebody
	 * replaced itself with now in o's keychain 
	 * @param o
	 * @throws KeyChainException
	 */
	public void checkRead(Object o) throws KeyChainException {
		KeyChain key = keyChain.get(o);
		T now = now();
		if(key != null)
			key.checkRead(now, o);
	}
	
	/**
	 * throws AccessException if now() cannot write o
	 * now can write o if it's the only key in o's keychain
	 * @param o
	 * @throws KeyChainException
	 */
	public void checkWrite(Object o) throws KeyChainException {
		KeyChain key = keyChain.get(o);
		T now = now();
		if(key != null)
			key.checkWrite(now, o);
	}
	
	private KeyChain getOrCreateKeyChain(T now, Object o) {
		KeyChain kc = keyChain.get(o);
		if(kc == null) {
			kc = new SingleKeyChain(now);
			KeyChain oldKC = keyChain.putIfAbsent(o, kc);
			if(oldKC != null) {
				kc = oldKC;
			}			
		}
		return kc;
	}
	/**
	 * now must own the slave; then slave's keychain is replaces with the master keychain
	 * the master's key chain must not have been linked to anybody or we throw an exception
	 * @param master
	 * @param slave
	 * @throws KeyChainException
	 */
	@SuppressWarnings("unchecked")
	public void linkKeychains(Object master, Object slave) throws KeyChainException {	
		//check that o is not a key for key to avoid cycles in the key chains
		KeyChain masterKey;
		KeyChain slaveKey;
		KeyChain newKey;
		T now = now();
		
		do {
			masterKey = getOrCreateKeyChain(now, master);
			slaveKey = getOrCreateKeyChain(now, slave);			
			if(! slaveKey.isOwnedBy(now) || (masterKey instanceof Permissions.DelegatingKeyChain)) {
				throw newLinkKeychainsException(now, masterKey, slaveKey);
			}
			newKey = new DelegatingKeyChain(master);
		} while(! keyChain.replace(slave, slaveKey, newKey));
	}
	
	/**
	 * now must be owner of slave and slave must have been linked to somebody else before
	 * then slave will get its own keychain again with now as the owner
	 * 
	 */
	@SuppressWarnings("unchecked")
	public void unlinkKeychains(Object slave) {
		//check that o is not a key for key to avoid cycles in the key chains
		KeyChain slaveKey;
		KeyChain newKey;
		T now = now();
		
		do {
			slaveKey = keyChain.get(slave);	
			if(slave == null || ! (slaveKey instanceof Permissions.DelegatingKeyChain) || ! slaveKey.isOwnedBy(now)) {
				throw newUnlinkKeychainsException(now, slaveKey);
			}
			newKey = new SingleKeyChain(now);
		} while(! keyChain.replace(slave, slaveKey, newKey));
	}
		
	/**
	 * add key to keychain of object o
	 * Throws AccessException if now cannot read o
	 * Lazily creates a single task key for now if no key is associated with o yet
	 * this can lead to unexpected results if o leaks to another task during construction
	 * @param o
	 * @param task
	 * @throws KeyChainException
	 */
	public void addTask(Object o, T task) throws KeyChainException {	
		//check that o is not a key for key to avoid cycles in the key chains
		KeyChain oKey;
		T now = now();
		KeyChain newKey;
		
		do {
			oKey = getOrCreateKeyChain(now, o);			
			newKey = oKey.add(now, task);
		} while(! keyChain.replace(o, oKey, newKey));			
	}
	
	/**
	 * replaces now with key in keychain of object o
	 * Throws AccessException if now cannot read o
	 * Lazily creates a single task key for now if no key is associated with o yet
	 * this can lead to unexpected results if o leaks to another task during construction
	 * @param o
	 * @param task
	 * @throws KeyChainException
	 */
	public void replaceNowWithTask(Object o, T task) throws KeyChainException  {		
		KeyChain oKey;
		T now = now();
		KeyChain newKey;
		do { 	
			oKey = getOrCreateKeyChain(now, o);			
			newKey = oKey.replace(now, task);			
		} while(! keyChain.replace(o, oKey, newKey));
	}
		
	public boolean isImmutable(Object o) {
		return getOrCreateKeyChain(now(), o).isImmutable();
	}
	
	public void makeImmutable(Object o) throws KeyChainException  {
		KeyChain oKey;
		T now = now();
		KeyChain newKey = immutableKey;
		
		do{
			oKey = getOrCreateKeyChain(now, o);
			oKey.checkWrite(now, o);
		} while(! keyChain.replace(o, oKey, newKey));		
	}
	
	public boolean isShared(Object o) {
		return getOrCreateKeyChain(now(), o).isShared();
	}
	
	public synchronized void makeShared(Object o) throws KeyChainException  {
		KeyChain oKey;
		T now = now();
		KeyChain newKey = sharedKey;
		
		do{
			oKey = getOrCreateKeyChain(now, o);
			//okey can be a SingleKeyChain with now or already shared
			oKey.checkWrite(now, o);
		} while(! keyChain.replace(o, oKey, newKey));		
	}
	
}
