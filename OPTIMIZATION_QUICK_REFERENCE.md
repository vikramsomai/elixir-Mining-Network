# ⚡ Optimization Quick Reference - Copy & Paste Solutions

## 🎯 Firebase Optimization Patterns

### Pattern 1: Single Listener (Not Multiple)
```java
// ❌ WRONG - Multiple listeners
userRef.addValueEventListener(listener1);
userRef.addValueEventListener(listener2);

// ✅ RIGHT - Single listener
private ValueEventListener userListener;

private void attachListener() {
    if (userListener != null) {
        userRef.removeEventListener(userListener);
    }
    
    userListener = new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot snapshot) {
            // Handle all data here
        }
        
        @Override
        public void onCancelled(DatabaseError error) {
            Log.e(TAG, "Error", error.toException());
        }
    };
    
    userRef.addValueEventListener(userListener);
}

@Override
public void onDestroyView() {
    super.onDestroyView();
    if (userListener != null) {
        userRef.removeEventListener(userListener);
    }
}
```

### Pattern 2: Batch Operations
```java
// ❌ WRONG - 3 Firebase calls
userRef.child("coins").setValue(100);
userRef.child("level").setValue(5);
userRef.child("streak").setValue(10);

// ✅ RIGHT - 1 Firebase call
Map<String, Object> updates = new HashMap<>();
updates.put("coins", 100);
updates.put("level", 5);
updates.put("streak", 10);
userRef.updateChildren(updates);
```

### Pattern 3: Caching with TTL
```java
// ✅ RIGHT - Cache with time-to-live
private String cachedData;
private long lastFetchTime = 0;
private static final long CACHE_TTL = 5 * 60 * 1000; // 5 minutes

private String getData() {
    long currentTime = System.currentTimeMillis();
    
    if (cachedData != null && (currentTime - lastFetchTime) < CACHE_TTL) {
        return cachedData; // Return cached
    }
    
    // Fetch fresh data
    cachedData = fetchFromFirebase();
    lastFetchTime = currentTime;
    return cachedData;
}
```

### Pattern 4: Query Optimization
```java
// ❌ WRONG - Fetches entire database
userRef.addValueEventListener(listener);

// ✅ RIGHT - Fetch only needed data
userRef.orderByChild("coins")
    .limitToFirst(100)
    .addValueEventListener(listener);
```

---

## 💾 Memory Optimization Patterns

### Pattern 1: Lazy Loading
```java
// ❌ WRONG - Loads all items
List<Item> items = new ArrayList<>();
for (DataSnapshot snap : snapshot.getChildren()) {
    items.add(snap.getValue(Item.class));
}

// ✅ RIGHT - Load only visible items
private static final int PAGE_SIZE = 20;
List<Item> visibleItems = new ArrayList<>();
for (int i = 0; i < Math.min(PAGE_SIZE, snapshot.getChildrenCount()); i++) {
    visibleItems.add(items.get(i));
}
```

### Pattern 2: Scaled Image Loading
```java
// ❌ WRONG - Loads full resolution
Bitmap bitmap = BitmapFactory.decodeFile(imagePath);

// ✅ RIGHT - Loads scaled bitmap
BitmapFactory.Options options = new BitmapFactory.Options();
options.inSampleSize = 2; // Load at 1/4 resolution
Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
```

### Pattern 3: Efficient Formatting
```java
// ❌ WRONG - Creates new formatter each time
String formatted = String.format("%.2f", value);

// ✅ RIGHT - Reuse formatter
private static final DecimalFormat formatter = new DecimalFormat("0.00");
String formatted = formatter.format(value);
```

### Pattern 4: Thread-Safe Collections
```java
// ❌ WRONG - Not thread-safe
private List<Listener> listeners = new ArrayList<>();

// ✅ RIGHT - Thread-safe
private final List<Listener> listeners = new CopyOnWriteArrayList<>();
```

---

## 🔋 Battery Optimization Patterns

### Pattern 1: Adaptive Sync Interval
```java
// ❌ WRONG - Always syncs every 1 second
handler.postDelayed(syncTask, 1000);

// ✅ RIGHT - Adaptive based on battery
private long getSyncInterval() {
    BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
    
    if (level < 20) return 10 * 60 * 1000; // 10 min
    if (level < 50) return 5 * 60 * 1000;  // 5 min
    return 2 * 60 * 1000;                  // 2 min
}

handler.postDelayed(syncTask, getSyncInterval());
```

### Pattern 2: Disable on Low Battery
```java
// ✅ RIGHT - Disable non-essential tasks
private void checkBatteryLevel() {
    BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
    
    if (level < 20) {
        disableBackgroundSync();
        disableAnalytics();
        disableNotifications();
    }
}
```

### Pattern 3: Reduce Animation Complexity
```java
// ❌ WRONG - Complex animation
ObjectAnimator animator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
animator.setDuration(1000);
animator.setInterpolator(new AccelerateDecelerateInterpolator());

// ✅ RIGHT - Simple animation
ObjectAnimator animator = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
animator.setDuration(200);
animator.setInterpolator(new LinearInterpolator());
```

---

## 🚀 Performance Optimization Patterns

### Pattern 1: Rate Calculation Caching
```java
// ❌ WRONG - Recalculates every time
public float getCurrentRate() {
    float rate = BASE_RATE;
    if (isAdWatched) rate *= AD_MULTIPLIER;
    if (hasPermanentBoost) rate *= PERMANENT_MULTIPLIER;
    return rate;
}

// ✅ RIGHT - Caches for 1 second
private float cachedRate = -1f;
private long lastCalculationTime = 0;
private static final long CACHE_DURATION = 1000;

public float getCurrentRate() {
    long currentTime = System.currentTimeMillis();
    
    if (cachedRate > 0 && (currentTime - lastCalculationTime) < CACHE_DURATION) {
        return cachedRate;
    }
    
    float rate = BASE_RATE;
    if (isAdWatched) rate *= AD_MULTIPLIER;
    if (hasPermanentBoost) rate *= PERMANENT_MULTIPLIER;
    
    cachedRate = rate;
    lastCalculationTime = currentTime;
    return rate;
}
```

### Pattern 2: Lazy Initialization
```java
// ❌ WRONG - Initializes immediately
private AdManager adManager = new AdManager();

// ✅ RIGHT - Initializes on demand
private AdManager adManager;

public AdManager getAdManager() {
    if (adManager == null) {
        adManager = new AdManager();
    }
    return adManager;
}
```

### Pattern 3: Object Reuse
```java
// ❌ WRONG - Creates new objects constantly
for (int i = 0; i < 1000; i++) {
    String text = new String("Item " + i);
    list.add(text);
}

// ✅ RIGHT - Reuses StringBuilder
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 1000; i++) {
    sb.setLength(0);
    sb.append("Item ").append(i);
    list.add(sb.toString());
}
```

---

## 📋 Implementation Checklist

### Firebase Optimization (2-3 hours)
- [ ] Consolidate listeners in HomeFragment
- [ ] Consolidate listeners in MiningFragment
- [ ] Consolidate listeners in LeaderBoardActivity
- [ ] Consolidate listeners in ReferralFragment
- [ ] Add listener cleanup in onDestroyView()
- [ ] Implement batch operations
- [ ] Add caching layer
- [ ] Test Firebase call count

### Memory Optimization (2-3 hours)
- [ ] Implement lazy loading in LeaderBoardActivity
- [ ] Add pagination for lists
- [ ] Optimize image loading
- [ ] Remove memory leaks
- [ ] Profile memory usage
- [ ] Test on 2GB device

### Battery Optimization (1-2 hours)
- [ ] Implement adaptive sync in MiningFragment
- [ ] Reduce background task frequency
- [ ] Optimize animation complexity
- [ ] Test battery drain
- [ ] Monitor background usage

### Testing (2-3 hours)
- [ ] Performance testing
- [ ] Low-end device testing
- [ ] Battery testing
- [ ] Firebase usage review
- [ ] Deploy to production

---

## 🎯 Performance Targets

### Firebase
- Calls: 50-60 → 5-10 (80% reduction)
- Data: 5-10MB → 1-2MB (80% reduction)
- Response: 2-3s → 300-500ms (70% improvement)

### Memory
- Heap: 150-200MB → 60-80MB (50% reduction)
- GC Pauses: 500-1000ms → 50-100ms (80% reduction)
- Leaks: Multiple → 0 (100% reduction)

### Battery
- Drain: 15-20% → 5-8% per hour (60% reduction)
- Tasks: 20+ → 5 (75% reduction)
- Sync: Every 1s → Every 5s (80% reduction)

---

## 🔍 Debugging Tips

### Check Firebase Calls
```java
private int callCount = 0;

private void logCall(String operation) {
    callCount++;
    Log.d(TAG, "Firebase call #" + callCount + ": " + operation);
}
```

### Monitor Memory
```java
private void logMemory() {
    Runtime runtime = Runtime.getRuntime();
    long used = runtime.totalMemory() - runtime.freeMemory();
    Log.d(TAG, "Memory: " + used / 1024 / 1024 + "MB");
}
```

### Check Battery
```java
private void logBattery() {
    BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
    Log.d(TAG, "Battery: " + level + "%");
}
```

---

## ✅ Success Criteria

- ✅ Firebase calls reduced by 70%+
- ✅ Response time improved by 60%+
- ✅ Memory usage reduced by 40%+
- ✅ Battery drain reduced by 50%+
- ✅ Works smoothly on 2GB devices
- ✅ No memory leaks
- ✅ All tests pass

---

## 📚 Full Documentation

- **PERFORMANCE_OPTIMIZATION_GUIDE.md** - Complete strategies
- **JAVA_CODE_OPTIMIZATION_GUIDE.md** - File-by-file guide
- **COMPLETE_OPTIMIZATION_SUMMARY.md** - Comprehensive summary

---

**Ready to optimize! Copy & paste these patterns into your code. 🚀**
