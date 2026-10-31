//region block: polyfills
if (typeof ArrayBuffer.isView === 'undefined') {
  ArrayBuffer.isView = function (a) {
    return a != null && a.__proto__ != null && a.__proto__.__proto__ === Int8Array.prototype.__proto__;
  };
}
if (typeof Array.prototype.fill === 'undefined') {
  // Polyfill from https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Array/fill#Polyfill
  Object.defineProperty(Array.prototype, 'fill', {value: function (value) {
    // Steps 1-2.
    if (this == null) {
      throw new TypeError('this is null or not defined');
    }
    var O = Object(this); // Steps 3-5.
    var len = O.length >>> 0; // Steps 6-7.
    var start = arguments[1];
    var relativeStart = start >> 0; // Step 8.
    var k = relativeStart < 0 ? Math.max(len + relativeStart, 0) : Math.min(relativeStart, len); // Steps 9-10.
    var end = arguments[2];
    var relativeEnd = end === undefined ? len : end >> 0; // Step 11.
    var finalValue = relativeEnd < 0 ? Math.max(len + relativeEnd, 0) : Math.min(relativeEnd, len); // Step 12.
    while (k < finalValue) {
      O[k] = value;
      k++;
    }
    ; // Step 13.
    return O;
  }});
}
[Int8Array, Int16Array, Uint16Array, Int32Array, Float32Array, Float64Array].forEach(function (TypedArray) {
  if (typeof TypedArray.prototype.fill === 'undefined') {
    Object.defineProperty(TypedArray.prototype, 'fill', {value: Array.prototype.fill});
  }
});
if (typeof Math.clz32 === 'undefined') {
  Math.clz32 = function (log, LN2) {
    return function (x) {
      var asUint = x >>> 0;
      if (asUint === 0) {
        return 32;
      }
      return 31 - (log(asUint) / LN2 | 0) | 0; // the "| 0" acts like math.floor
    };
  }(Math.log, Math.LN2);
}
if (typeof Math.imul === 'undefined') {
  Math.imul = function imul(a, b) {
    return (a & 4.29490176E9) * (b & 65535) + (a & 65535) * (b | 0) | 0;
  };
}
//endregion
(function (root, factory) {
  if (typeof define === 'function' && define.amd)
    define(['exports'], factory);
  else if (typeof exports === 'object')
    factory(module.exports);
  else
    root['PatchRelay:screens'] = factory(typeof this['PatchRelay:screens'] === 'undefined' ? {} : this['PatchRelay:screens']);
}(this, function (_) {
  'use strict';
  //region block: imports
  var clz32 = Math.clz32;
  var imul = Math.imul;
  var isView = ArrayBuffer.isView;
  //endregion
  //region block: pre-declaration
  setMetadataFor(CharSequence, 'CharSequence', interfaceMeta);
  setMetadataFor(Number_0, 'Number', classMeta);
  setMetadataFor(Unit, 'Unit', objectMeta);
  setMetadataFor(IntCompanionObject, 'IntCompanionObject', objectMeta);
  setMetadataFor(Collection, 'Collection', interfaceMeta);
  setMetadataFor(AbstractCollection, 'AbstractCollection', classMeta, VOID, [Collection]);
  setMetadataFor(AbstractMutableCollection, 'AbstractMutableCollection', classMeta, AbstractCollection, [AbstractCollection, Collection]);
  setMetadataFor(IteratorImpl, 'IteratorImpl', classMeta);
  setMetadataFor(List, 'List', interfaceMeta, VOID, [Collection]);
  setMetadataFor(AbstractMutableList, 'AbstractMutableList', classMeta, AbstractMutableCollection, [AbstractMutableCollection, Collection, List]);
  setMetadataFor(Set, 'Set', interfaceMeta, VOID, [Collection]);
  setMetadataFor(AbstractMutableSet, 'AbstractMutableSet', classMeta, AbstractMutableCollection, [AbstractMutableCollection, Set, Collection]);
  setMetadataFor(Companion, 'Companion', objectMeta);
  setMetadataFor(ArrayList, 'ArrayList', classMeta, AbstractMutableList, [AbstractMutableList, Collection, List], ArrayList_init_$Create$);
  setMetadataFor(HashSet, 'HashSet', classMeta, AbstractMutableSet, [AbstractMutableSet, Set, Collection], HashSet_init_$Create$);
  setMetadataFor(Companion_0, 'Companion', objectMeta);
  setMetadataFor(Itr, 'Itr', classMeta);
  setMetadataFor(KeysItr, 'KeysItr', classMeta, Itr);
  setMetadataFor(EntriesItr, 'EntriesItr', classMeta, Itr);
  setMetadataFor(Entry, 'Entry', interfaceMeta);
  setMetadataFor(EntryRef, 'EntryRef', classMeta, VOID, [Entry]);
  function containsAllEntries(m) {
    var tmp$ret$0;
    $l$block_0: {
      // Inline function 'kotlin.collections.all' call
      var tmp;
      if (isInterface(m, Collection)) {
        tmp = m.n();
      } else {
        tmp = false;
      }
      if (tmp) {
        tmp$ret$0 = true;
        break $l$block_0;
      }
      var tmp0_iterator = m.k();
      while (tmp0_iterator.r()) {
        var element = tmp0_iterator.s();
        // Inline function 'kotlin.collections.InternalMap.containsAllEntries.<anonymous>' call
        // Inline function 'kotlin.js.unsafeCast' call
        // Inline function 'kotlin.js.asDynamic' call
        var entry = element;
        var tmp_0;
        if (!(entry == null) ? isInterface(entry, Entry) : false) {
          tmp_0 = this.e3(entry);
        } else {
          tmp_0 = false;
        }
        if (!tmp_0) {
          tmp$ret$0 = false;
          break $l$block_0;
        }
      }
      tmp$ret$0 = true;
    }
    return tmp$ret$0;
  }
  setMetadataFor(InternalMap, 'InternalMap', interfaceMeta);
  setMetadataFor(InternalHashMap, 'InternalHashMap', classMeta, VOID, [InternalMap], InternalHashMap_init_$Create$);
  setMetadataFor(LinkedHashSet, 'LinkedHashSet', classMeta, HashSet, [HashSet, Set, Collection], LinkedHashSet_init_$Create$);
  setMetadataFor(StringBuilder, 'StringBuilder', classMeta, VOID, [CharSequence], StringBuilder_init_$Create$_0);
  setMetadataFor(Companion_1, 'Companion', objectMeta);
  setMetadataFor(Regex, 'Regex', classMeta);
  setMetadataFor(Companion_2, 'Companion', objectMeta);
  setMetadataFor(Char, 'Char', classMeta);
  setMetadataFor(Map_0, 'Map', interfaceMeta);
  setMetadataFor(Companion_3, 'Companion', objectMeta);
  setMetadataFor(Long, 'Long', classMeta, Number_0);
  setMetadataFor(Exception, 'Exception', classMeta, Error, VOID, Exception_init_$Create$);
  setMetadataFor(RuntimeException, 'RuntimeException', classMeta, Exception, VOID, RuntimeException_init_$Create$);
  setMetadataFor(IllegalArgumentException, 'IllegalArgumentException', classMeta, RuntimeException, VOID, IllegalArgumentException_init_$Create$);
  setMetadataFor(IndexOutOfBoundsException, 'IndexOutOfBoundsException', classMeta, RuntimeException, VOID, IndexOutOfBoundsException_init_$Create$);
  setMetadataFor(IllegalStateException, 'IllegalStateException', classMeta, RuntimeException, VOID, IllegalStateException_init_$Create$);
  setMetadataFor(UnsupportedOperationException, 'UnsupportedOperationException', classMeta, RuntimeException, VOID, UnsupportedOperationException_init_$Create$);
  setMetadataFor(NoSuchElementException, 'NoSuchElementException', classMeta, RuntimeException, VOID, NoSuchElementException_init_$Create$);
  setMetadataFor(ConcurrentModificationException, 'ConcurrentModificationException', classMeta, RuntimeException, VOID, ConcurrentModificationException_init_$Create$);
  setMetadataFor(NullPointerException, 'NullPointerException', classMeta, RuntimeException, VOID, NullPointerException_init_$Create$);
  setMetadataFor(ClassCastException, 'ClassCastException', classMeta, RuntimeException, VOID, ClassCastException_init_$Create$);
  setMetadataFor(Companion_4, 'Companion', objectMeta);
  setMetadataFor(Companion_5, 'Companion', objectMeta);
  setMetadataFor(EmptyList, 'EmptyList', objectMeta, VOID, [List]);
  setMetadataFor(EmptyIterator, 'EmptyIterator', objectMeta);
  setMetadataFor(EmptySet, 'EmptySet', objectMeta, VOID, [Set]);
  //endregion
  function CharSequence() {
  }
  function Number_0() {
  }
  function Unit() {
  }
  protoOf(Unit).toString = function () {
    return 'kotlin.Unit';
  };
  var Unit_instance;
  function Unit_getInstance() {
    return Unit_instance;
  }
  function IntCompanionObject() {
    this.MIN_VALUE = -2147483648;
    this.MAX_VALUE = 2147483647;
    this.SIZE_BYTES = 4;
    this.SIZE_BITS = 32;
  }
  protoOf(IntCompanionObject).d = function () {
    return this.MIN_VALUE;
  };
  protoOf(IntCompanionObject).e = function () {
    return this.MAX_VALUE;
  };
  protoOf(IntCompanionObject).f = function () {
    return this.SIZE_BYTES;
  };
  protoOf(IntCompanionObject).g = function () {
    return this.SIZE_BITS;
  };
  var IntCompanionObject_instance;
  function IntCompanionObject_getInstance() {
    return IntCompanionObject_instance;
  }
  function isNaN_0(_this__u8e3s4) {
    return !(_this__u8e3s4 === _this__u8e3s4);
  }
  function takeHighestOneBit(_this__u8e3s4) {
    var tmp;
    if (_this__u8e3s4 === 0) {
      tmp = 0;
    } else {
      var tmp_0 = 32 - 1 | 0;
      // Inline function 'kotlin.countLeadingZeroBits' call
      tmp = 1 << (tmp_0 - clz32(_this__u8e3s4) | 0);
    }
    return tmp;
  }
  function collectionToArray(collection) {
    return collectionToArrayCommonImpl(collection);
  }
  function setOf(element) {
    return hashSetOf([element]);
  }
  function mapCapacity(expectedSize) {
    return expectedSize;
  }
  function AbstractMutableCollection() {
    AbstractCollection.call(this);
  }
  protoOf(AbstractMutableCollection).toJSON = function () {
    return this.toArray();
  };
  protoOf(AbstractMutableCollection).i = function () {
  };
  function IteratorImpl($outer) {
    this.q_1 = $outer;
    this.o_1 = 0;
    this.p_1 = -1;
  }
  protoOf(IteratorImpl).r = function () {
    return this.o_1 < this.q_1.j();
  };
  protoOf(IteratorImpl).s = function () {
    if (!this.r())
      throw NoSuchElementException_init_$Create$();
    var tmp = this;
    var tmp1 = this.o_1;
    this.o_1 = tmp1 + 1 | 0;
    tmp.p_1 = tmp1;
    return this.q_1.t(this.p_1);
  };
  function AbstractMutableList() {
    AbstractMutableCollection.call(this);
    this.u_1 = 0;
  }
  protoOf(AbstractMutableList).h = function (element) {
    this.i();
    this.v(this.j(), element);
    return true;
  };
  protoOf(AbstractMutableList).k = function () {
    return new IteratorImpl(this);
  };
  protoOf(AbstractMutableList).l = function (element) {
    return this.w(element) >= 0;
  };
  protoOf(AbstractMutableList).w = function (element) {
    var tmp$ret$1;
    $l$block: {
      // Inline function 'kotlin.collections.indexOfFirst' call
      var index = 0;
      var tmp0_iterator = this.k();
      while (tmp0_iterator.r()) {
        var item = tmp0_iterator.s();
        // Inline function 'kotlin.collections.AbstractMutableList.indexOf.<anonymous>' call
        if (equals(item, element)) {
          tmp$ret$1 = index;
          break $l$block;
        }
        index = index + 1 | 0;
      }
      tmp$ret$1 = -1;
    }
    return tmp$ret$1;
  };
  protoOf(AbstractMutableList).equals = function (other) {
    if (other === this)
      return true;
    if (!(!(other == null) ? isInterface(other, List) : false))
      return false;
    return Companion_instance_4.y(this, other);
  };
  protoOf(AbstractMutableList).hashCode = function () {
    return Companion_instance_4.z(this);
  };
  function AbstractMutableSet() {
    AbstractMutableCollection.call(this);
  }
  protoOf(AbstractMutableSet).equals = function (other) {
    if (other === this)
      return true;
    if (!(!(other == null) ? isInterface(other, Set) : false))
      return false;
    return Companion_instance_5.a1(this, other);
  };
  protoOf(AbstractMutableSet).hashCode = function () {
    return Companion_instance_5.b1(this);
  };
  function arrayOfUninitializedElements(capacity) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(capacity >= 0)) {
      // Inline function 'kotlin.collections.arrayOfUninitializedElements.<anonymous>' call
      var message = 'capacity must be non-negative.';
      throw IllegalArgumentException_init_$Create$_0(toString_2(message));
    }
    // Inline function 'kotlin.js.unsafeCast' call
    // Inline function 'kotlin.arrayOfNulls' call
    // Inline function 'kotlin.js.asDynamic' call
    return fillArrayVal(Array(capacity), null);
  }
  function resetRange(_this__u8e3s4, fromIndex, toIndex) {
    // Inline function 'kotlin.js.nativeFill' call
    // Inline function 'kotlin.js.asDynamic' call
    _this__u8e3s4.fill(null, fromIndex, toIndex);
  }
  function copyOfUninitializedElements(_this__u8e3s4, newSize) {
    // Inline function 'kotlin.js.unsafeCast' call
    // Inline function 'kotlin.js.asDynamic' call
    return copyOf_0(_this__u8e3s4, newSize);
  }
  function Companion() {
    Companion_instance = this;
    var tmp = this;
    // Inline function 'kotlin.also' call
    var this_0 = ArrayList_init_$Create$_0(0);
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlin.collections.Companion.Empty.<anonymous>' call
    this_0.e1_1 = true;
    tmp.f1_1 = this_0;
  }
  var Companion_instance;
  function Companion_getInstance() {
    if (Companion_instance == null)
      new Companion();
    return Companion_instance;
  }
  function ArrayList_init_$Init$($this) {
    // Inline function 'kotlin.emptyArray' call
    var tmp$ret$0 = [];
    ArrayList.call($this, tmp$ret$0);
    return $this;
  }
  function ArrayList_init_$Create$() {
    return ArrayList_init_$Init$(objectCreate(protoOf(ArrayList)));
  }
  function ArrayList_init_$Init$_0(initialCapacity, $this) {
    // Inline function 'kotlin.emptyArray' call
    var tmp$ret$0 = [];
    ArrayList.call($this, tmp$ret$0);
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(initialCapacity >= 0)) {
      // Inline function 'kotlin.collections.ArrayList.<init>.<anonymous>' call
      var message = 'Negative initial capacity: ' + initialCapacity;
      throw IllegalArgumentException_init_$Create$_0(toString_2(message));
    }
    return $this;
  }
  function ArrayList_init_$Create$_0(initialCapacity) {
    return ArrayList_init_$Init$_0(initialCapacity, objectCreate(protoOf(ArrayList)));
  }
  function rangeCheck($this, index) {
    // Inline function 'kotlin.apply' call
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlin.collections.ArrayList.rangeCheck.<anonymous>' call
    Companion_instance_4.g1(index, $this.j());
    return index;
  }
  function insertionRangeCheck($this, index) {
    // Inline function 'kotlin.apply' call
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlin.collections.ArrayList.insertionRangeCheck.<anonymous>' call
    Companion_instance_4.h1(index, $this.j());
    return index;
  }
  function ArrayList(array) {
    Companion_getInstance();
    AbstractMutableList.call(this);
    this.d1_1 = array;
    this.e1_1 = false;
  }
  protoOf(ArrayList).j = function () {
    return this.d1_1.length;
  };
  protoOf(ArrayList).t = function (index) {
    var tmp = this.d1_1[rangeCheck(this, index)];
    return (tmp == null ? true : !(tmp == null)) ? tmp : THROW_CCE();
  };
  protoOf(ArrayList).h = function (element) {
    this.i();
    // Inline function 'kotlin.js.asDynamic' call
    this.d1_1.push(element);
    this.u_1 = this.u_1 + 1 | 0;
    return true;
  };
  protoOf(ArrayList).v = function (index, element) {
    this.i();
    // Inline function 'kotlin.js.asDynamic' call
    this.d1_1.splice(insertionRangeCheck(this, index), 0, element);
    this.u_1 = this.u_1 + 1 | 0;
  };
  protoOf(ArrayList).w = function (element) {
    return indexOf(this.d1_1, element);
  };
  protoOf(ArrayList).toString = function () {
    return arrayToString(this.d1_1);
  };
  protoOf(ArrayList).i1 = function () {
    return [].slice.call(this.d1_1);
  };
  protoOf(ArrayList).toArray = function () {
    return this.i1();
  };
  protoOf(ArrayList).i = function () {
    if (this.e1_1)
      throw UnsupportedOperationException_init_$Create$();
  };
  function HashSet_init_$Init$(map, $this) {
    AbstractMutableSet.call($this);
    HashSet.call($this);
    $this.j1_1 = map;
    return $this;
  }
  function HashSet_init_$Init$_0($this) {
    HashSet_init_$Init$(InternalHashMap_init_$Create$(), $this);
    return $this;
  }
  function HashSet_init_$Create$() {
    return HashSet_init_$Init$_0(objectCreate(protoOf(HashSet)));
  }
  function HashSet_init_$Init$_1(initialCapacity, loadFactor, $this) {
    HashSet_init_$Init$(InternalHashMap_init_$Create$_0(initialCapacity, loadFactor), $this);
    return $this;
  }
  function HashSet_init_$Init$_2(initialCapacity, $this) {
    HashSet_init_$Init$_1(initialCapacity, 1.0, $this);
    return $this;
  }
  function HashSet_init_$Create$_0(initialCapacity) {
    return HashSet_init_$Init$_2(initialCapacity, objectCreate(protoOf(HashSet)));
  }
  protoOf(HashSet).h = function (element) {
    return this.j1_1.k1(element, true) == null;
  };
  protoOf(HashSet).l = function (element) {
    return this.j1_1.l1(element);
  };
  protoOf(HashSet).n = function () {
    return this.j1_1.j() === 0;
  };
  protoOf(HashSet).k = function () {
    return this.j1_1.m1();
  };
  protoOf(HashSet).j = function () {
    return this.j1_1.j();
  };
  function HashSet() {
  }
  function computeHashSize($this, capacity) {
    return takeHighestOneBit(imul(coerceAtLeast(capacity, 1), 3));
  }
  function computeShift($this, hashSize) {
    // Inline function 'kotlin.countLeadingZeroBits' call
    return clz32(hashSize) + 1 | 0;
  }
  function InternalHashMap_init_$Init$($this) {
    InternalHashMap_init_$Init$_0(8, $this);
    return $this;
  }
  function InternalHashMap_init_$Create$() {
    return InternalHashMap_init_$Init$(objectCreate(protoOf(InternalHashMap)));
  }
  function InternalHashMap_init_$Init$_0(initialCapacity, $this) {
    InternalHashMap.call($this, arrayOfUninitializedElements(initialCapacity), null, new Int32Array(initialCapacity), new Int32Array(computeHashSize(Companion_instance_0, initialCapacity)), 2, 0);
    return $this;
  }
  function InternalHashMap_init_$Init$_1(initialCapacity, loadFactor, $this) {
    InternalHashMap_init_$Init$_0(initialCapacity, $this);
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(loadFactor > 0.0)) {
      // Inline function 'kotlin.collections.InternalHashMap.<init>.<anonymous>' call
      var message = 'Non-positive load factor: ' + loadFactor;
      throw IllegalArgumentException_init_$Create$_0(toString_2(message));
    }
    return $this;
  }
  function InternalHashMap_init_$Create$_0(initialCapacity, loadFactor) {
    return InternalHashMap_init_$Init$_1(initialCapacity, loadFactor, objectCreate(protoOf(InternalHashMap)));
  }
  function _get_capacity__a9k9f3($this) {
    return $this.n1_1.length;
  }
  function _get_hashSize__tftcho($this) {
    return $this.q1_1.length;
  }
  function registerModification($this) {
    $this.u1_1 = $this.u1_1 + 1 | 0;
  }
  function ensureExtraCapacity($this, n) {
    if (shouldCompact($this, n)) {
      rehash($this, _get_hashSize__tftcho($this));
    } else {
      ensureCapacity($this, $this.s1_1 + n | 0);
    }
  }
  function shouldCompact($this, extraCapacity) {
    var spareCapacity = _get_capacity__a9k9f3($this) - $this.s1_1 | 0;
    var gaps = $this.s1_1 - $this.j() | 0;
    return (spareCapacity < extraCapacity ? (gaps + spareCapacity | 0) >= extraCapacity : false) ? gaps >= (_get_capacity__a9k9f3($this) / 4 | 0) : false;
  }
  function ensureCapacity($this, minCapacity) {
    if (minCapacity < 0)
      throw RuntimeException_init_$Create$_0('too many elements');
    if (minCapacity > _get_capacity__a9k9f3($this)) {
      var newSize = Companion_instance_4.x1(_get_capacity__a9k9f3($this), minCapacity);
      $this.n1_1 = copyOfUninitializedElements($this.n1_1, newSize);
      var tmp = $this;
      var tmp0_safe_receiver = $this.o1_1;
      tmp.o1_1 = tmp0_safe_receiver == null ? null : copyOfUninitializedElements(tmp0_safe_receiver, newSize);
      $this.p1_1 = copyOf($this.p1_1, newSize);
      var newHashSize = computeHashSize(Companion_instance_0, newSize);
      if (newHashSize > _get_hashSize__tftcho($this)) {
        rehash($this, newHashSize);
      }
    }
  }
  function allocateValuesArray($this) {
    var curValuesArray = $this.o1_1;
    if (!(curValuesArray == null))
      return curValuesArray;
    var newValuesArray = arrayOfUninitializedElements(_get_capacity__a9k9f3($this));
    $this.o1_1 = newValuesArray;
    return newValuesArray;
  }
  function hash($this, key) {
    return key == null ? 0 : imul(hashCode(key), -1640531527) >>> $this.t1_1 | 0;
  }
  function compact($this) {
    var i = 0;
    var j = 0;
    var valuesArray = $this.o1_1;
    while (i < $this.s1_1) {
      if ($this.p1_1[i] >= 0) {
        $this.n1_1[j] = $this.n1_1[i];
        if (!(valuesArray == null)) {
          valuesArray[j] = valuesArray[i];
        }
        j = j + 1 | 0;
      }
      i = i + 1 | 0;
    }
    resetRange($this.n1_1, j, $this.s1_1);
    if (valuesArray == null)
      null;
    else {
      resetRange(valuesArray, j, $this.s1_1);
    }
    $this.s1_1 = j;
  }
  function rehash($this, newHashSize) {
    registerModification($this);
    if ($this.s1_1 > $this.v1_1) {
      compact($this);
    }
    if (!(newHashSize === _get_hashSize__tftcho($this))) {
      $this.q1_1 = new Int32Array(newHashSize);
      $this.t1_1 = computeShift(Companion_instance_0, newHashSize);
    } else {
      fill($this.q1_1, 0, 0, _get_hashSize__tftcho($this));
    }
    var i = 0;
    while (i < $this.s1_1) {
      var tmp0 = i;
      i = tmp0 + 1 | 0;
      if (!putRehash($this, tmp0)) {
        throw IllegalStateException_init_$Create$_0('This cannot happen with fixed magic multiplier and grow-only hash array. Have object hashCodes changed?');
      }
    }
  }
  function putRehash($this, i) {
    var hash_0 = hash($this, $this.n1_1[i]);
    var probesLeft = $this.r1_1;
    while (true) {
      var index = $this.q1_1[hash_0];
      if (index === 0) {
        $this.q1_1[hash_0] = i + 1 | 0;
        $this.p1_1[i] = hash_0;
        return true;
      }
      probesLeft = probesLeft - 1 | 0;
      if (probesLeft < 0)
        return false;
      var tmp0 = hash_0;
      hash_0 = tmp0 - 1 | 0;
      if (tmp0 === 0)
        hash_0 = _get_hashSize__tftcho($this) - 1 | 0;
    }
  }
  function findKey($this, key) {
    var hash_0 = hash($this, key);
    var probesLeft = $this.r1_1;
    while (true) {
      var index = $this.q1_1[hash_0];
      if (index === 0)
        return -1;
      if (index > 0 ? equals($this.n1_1[index - 1 | 0], key) : false)
        return index - 1 | 0;
      probesLeft = probesLeft - 1 | 0;
      if (probesLeft < 0)
        return -1;
      var tmp0 = hash_0;
      hash_0 = tmp0 - 1 | 0;
      if (tmp0 === 0)
        hash_0 = _get_hashSize__tftcho($this) - 1 | 0;
    }
  }
  function addKey($this, key) {
    $this.i();
    retry: while (true) {
      var hash_0 = hash($this, key);
      var tentativeMaxProbeDistance = coerceAtMost(imul($this.r1_1, 2), _get_hashSize__tftcho($this) / 2 | 0);
      var probeDistance = 0;
      while (true) {
        var index = $this.q1_1[hash_0];
        if (index <= 0) {
          if ($this.s1_1 >= _get_capacity__a9k9f3($this)) {
            ensureExtraCapacity($this, 1);
            continue retry;
          }
          var tmp1 = $this.s1_1;
          $this.s1_1 = tmp1 + 1 | 0;
          var putIndex = tmp1;
          $this.n1_1[putIndex] = key;
          $this.p1_1[putIndex] = hash_0;
          $this.q1_1[hash_0] = putIndex + 1 | 0;
          $this.v1_1 = $this.v1_1 + 1 | 0;
          registerModification($this);
          if (probeDistance > $this.r1_1)
            $this.r1_1 = probeDistance;
          return putIndex;
        }
        if (equals($this.n1_1[index - 1 | 0], key)) {
          return -index | 0;
        }
        probeDistance = probeDistance + 1 | 0;
        if (probeDistance > tentativeMaxProbeDistance) {
          rehash($this, imul(_get_hashSize__tftcho($this), 2));
          continue retry;
        }
        var tmp4 = hash_0;
        hash_0 = tmp4 - 1 | 0;
        if (tmp4 === 0)
          hash_0 = _get_hashSize__tftcho($this) - 1 | 0;
      }
    }
  }
  function contentEquals($this, other) {
    return $this.v1_1 === other.j() ? $this.z1(other.y1()) : false;
  }
  function Companion_0() {
    this.a2_1 = -1640531527;
    this.b2_1 = 8;
    this.c2_1 = 2;
    this.d2_1 = -1;
  }
  var Companion_instance_0;
  function Companion_getInstance_0() {
    return Companion_instance_0;
  }
  function Itr(map) {
    this.e2_1 = map;
    this.f2_1 = 0;
    this.g2_1 = -1;
    this.h2_1 = this.e2_1.u1_1;
    this.i2();
  }
  protoOf(Itr).i2 = function () {
    while (this.f2_1 < this.e2_1.s1_1 ? this.e2_1.p1_1[this.f2_1] < 0 : false) {
      this.f2_1 = this.f2_1 + 1 | 0;
    }
  };
  protoOf(Itr).r = function () {
    return this.f2_1 < this.e2_1.s1_1;
  };
  protoOf(Itr).j2 = function () {
    if (!(this.e2_1.u1_1 === this.h2_1))
      throw ConcurrentModificationException_init_$Create$();
  };
  function KeysItr(map) {
    Itr.call(this, map);
  }
  protoOf(KeysItr).s = function () {
    this.j2();
    if (this.f2_1 >= this.e2_1.s1_1)
      throw NoSuchElementException_init_$Create$();
    var tmp = this;
    var tmp1 = this.f2_1;
    this.f2_1 = tmp1 + 1 | 0;
    tmp.g2_1 = tmp1;
    var result = this.e2_1.n1_1[this.g2_1];
    this.i2();
    return result;
  };
  function EntriesItr(map) {
    Itr.call(this, map);
  }
  protoOf(EntriesItr).s = function () {
    this.j2();
    if (this.f2_1 >= this.e2_1.s1_1)
      throw NoSuchElementException_init_$Create$();
    var tmp = this;
    var tmp1 = this.f2_1;
    this.f2_1 = tmp1 + 1 | 0;
    tmp.g2_1 = tmp1;
    var result = new EntryRef(this.e2_1, this.g2_1);
    this.i2();
    return result;
  };
  protoOf(EntriesItr).s2 = function () {
    if (this.f2_1 >= this.e2_1.s1_1)
      throw NoSuchElementException_init_$Create$();
    var tmp = this;
    var tmp1 = this.f2_1;
    this.f2_1 = tmp1 + 1 | 0;
    tmp.g2_1 = tmp1;
    // Inline function 'kotlin.hashCode' call
    var tmp0_safe_receiver = this.e2_1.n1_1[this.g2_1];
    var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : hashCode(tmp0_safe_receiver);
    var tmp_0 = tmp1_elvis_lhs == null ? 0 : tmp1_elvis_lhs;
    // Inline function 'kotlin.hashCode' call
    var tmp0_safe_receiver_0 = ensureNotNull(this.e2_1.o1_1)[this.g2_1];
    var tmp1_elvis_lhs_0 = tmp0_safe_receiver_0 == null ? null : hashCode(tmp0_safe_receiver_0);
    var result = tmp_0 ^ (tmp1_elvis_lhs_0 == null ? 0 : tmp1_elvis_lhs_0);
    this.i2();
    return result;
  };
  protoOf(EntriesItr).t2 = function (sb) {
    if (this.f2_1 >= this.e2_1.s1_1)
      throw NoSuchElementException_init_$Create$();
    var tmp = this;
    var tmp1 = this.f2_1;
    this.f2_1 = tmp1 + 1 | 0;
    tmp.g2_1 = tmp1;
    var key = this.e2_1.n1_1[this.g2_1];
    if (equals(key, this.e2_1)) {
      sb.w2('(this Map)');
    } else {
      sb.v2(key);
    }
    sb.x2(_Char___init__impl__6a9atx(61));
    var value = ensureNotNull(this.e2_1.o1_1)[this.g2_1];
    if (equals(value, this.e2_1)) {
      sb.w2('(this Map)');
    } else {
      sb.v2(value);
    }
    this.i2();
  };
  function EntryRef(map, index) {
    this.y2_1 = map;
    this.z2_1 = index;
  }
  protoOf(EntryRef).a3 = function () {
    return this.y2_1.n1_1[this.z2_1];
  };
  protoOf(EntryRef).b3 = function () {
    return ensureNotNull(this.y2_1.o1_1)[this.z2_1];
  };
  protoOf(EntryRef).equals = function (other) {
    var tmp;
    var tmp_0;
    if (!(other == null) ? isInterface(other, Entry) : false) {
      tmp_0 = equals(other.a3(), this.a3());
    } else {
      tmp_0 = false;
    }
    if (tmp_0) {
      tmp = equals(other.b3(), this.b3());
    } else {
      tmp = false;
    }
    return tmp;
  };
  protoOf(EntryRef).hashCode = function () {
    // Inline function 'kotlin.hashCode' call
    var tmp0_safe_receiver = this.a3();
    var tmp1_elvis_lhs = tmp0_safe_receiver == null ? null : hashCode(tmp0_safe_receiver);
    var tmp = tmp1_elvis_lhs == null ? 0 : tmp1_elvis_lhs;
    // Inline function 'kotlin.hashCode' call
    var tmp0_safe_receiver_0 = this.b3();
    var tmp1_elvis_lhs_0 = tmp0_safe_receiver_0 == null ? null : hashCode(tmp0_safe_receiver_0);
    return tmp ^ (tmp1_elvis_lhs_0 == null ? 0 : tmp1_elvis_lhs_0);
  };
  protoOf(EntryRef).toString = function () {
    return '' + this.a3() + '=' + this.b3();
  };
  function InternalHashMap(keysArray, valuesArray, presenceArray, hashArray, maxProbeDistance, length) {
    this.n1_1 = keysArray;
    this.o1_1 = valuesArray;
    this.p1_1 = presenceArray;
    this.q1_1 = hashArray;
    this.r1_1 = maxProbeDistance;
    this.s1_1 = length;
    this.t1_1 = computeShift(Companion_instance_0, _get_hashSize__tftcho(this));
    this.u1_1 = 0;
    this.v1_1 = 0;
    this.w1_1 = false;
  }
  protoOf(InternalHashMap).j = function () {
    return this.v1_1;
  };
  protoOf(InternalHashMap).l1 = function (key) {
    return findKey(this, key) >= 0;
  };
  protoOf(InternalHashMap).k1 = function (key, value) {
    var index = addKey(this, key);
    var valuesArray = allocateValuesArray(this);
    if (index < 0) {
      var oldValue = valuesArray[(-index | 0) - 1 | 0];
      valuesArray[(-index | 0) - 1 | 0] = value;
      return oldValue;
    } else {
      valuesArray[index] = value;
      return null;
    }
  };
  protoOf(InternalHashMap).equals = function (other) {
    var tmp;
    if (other === this) {
      tmp = true;
    } else {
      var tmp_0;
      if (!(other == null) ? isInterface(other, Map_0) : false) {
        tmp_0 = contentEquals(this, other);
      } else {
        tmp_0 = false;
      }
      tmp = tmp_0;
    }
    return tmp;
  };
  protoOf(InternalHashMap).hashCode = function () {
    var result = 0;
    var it = this.c3();
    while (it.r()) {
      result = result + it.s2() | 0;
    }
    return result;
  };
  protoOf(InternalHashMap).toString = function () {
    var sb = StringBuilder_init_$Create$(2 + imul(this.v1_1, 3) | 0);
    sb.w2('{');
    var i = 0;
    var it = this.c3();
    while (it.r()) {
      if (i > 0) {
        sb.w2(', ');
      }
      it.t2(sb);
      i = i + 1 | 0;
    }
    sb.w2('}');
    return sb.toString();
  };
  protoOf(InternalHashMap).i = function () {
    if (this.w1_1)
      throw UnsupportedOperationException_init_$Create$();
  };
  protoOf(InternalHashMap).d3 = function (entry) {
    var index = findKey(this, entry.a3());
    if (index < 0)
      return false;
    return equals(ensureNotNull(this.o1_1)[index], entry.b3());
  };
  protoOf(InternalHashMap).e3 = function (entry) {
    return this.d3(isInterface(entry, Entry) ? entry : THROW_CCE());
  };
  protoOf(InternalHashMap).m1 = function () {
    return new KeysItr(this);
  };
  protoOf(InternalHashMap).c3 = function () {
    return new EntriesItr(this);
  };
  function InternalMap() {
  }
  function LinkedHashSet_init_$Init$($this) {
    HashSet_init_$Init$_0($this);
    LinkedHashSet.call($this);
    return $this;
  }
  function LinkedHashSet_init_$Create$() {
    return LinkedHashSet_init_$Init$(objectCreate(protoOf(LinkedHashSet)));
  }
  function LinkedHashSet_init_$Init$_0(initialCapacity, loadFactor, $this) {
    HashSet_init_$Init$_1(initialCapacity, loadFactor, $this);
    LinkedHashSet.call($this);
    return $this;
  }
  function LinkedHashSet_init_$Init$_1(initialCapacity, $this) {
    LinkedHashSet_init_$Init$_0(initialCapacity, 1.0, $this);
    return $this;
  }
  function LinkedHashSet_init_$Create$_0(initialCapacity) {
    return LinkedHashSet_init_$Init$_1(initialCapacity, objectCreate(protoOf(LinkedHashSet)));
  }
  function LinkedHashSet() {
  }
  function reset(_this__u8e3s4) {
    _this__u8e3s4.lastIndex = 0;
  }
  function StringBuilder_init_$Init$(capacity, $this) {
    StringBuilder_init_$Init$_0($this);
    return $this;
  }
  function StringBuilder_init_$Create$(capacity) {
    return StringBuilder_init_$Init$(capacity, objectCreate(protoOf(StringBuilder)));
  }
  function StringBuilder_init_$Init$_0($this) {
    StringBuilder.call($this, '');
    return $this;
  }
  function StringBuilder_init_$Create$_0() {
    return StringBuilder_init_$Init$_0(objectCreate(protoOf(StringBuilder)));
  }
  function StringBuilder(content) {
    this.u2_1 = !(content === undefined) ? content : '';
  }
  protoOf(StringBuilder).a = function () {
    // Inline function 'kotlin.js.asDynamic' call
    return this.u2_1.length;
  };
  protoOf(StringBuilder).b = function (index) {
    // Inline function 'kotlin.text.getOrElse' call
    var this_0 = this.u2_1;
    var tmp;
    if (index >= 0 ? index <= get_lastIndex(this_0) : false) {
      tmp = charSequenceGet(this_0, index);
    } else {
      throw IndexOutOfBoundsException_init_$Create$_0('index: ' + index + ', length: ' + this.a() + '}');
    }
    return tmp;
  };
  protoOf(StringBuilder).c = function (startIndex, endIndex) {
    // Inline function 'kotlin.text.substring' call
    // Inline function 'kotlin.js.asDynamic' call
    return this.u2_1.substring(startIndex, endIndex);
  };
  protoOf(StringBuilder).x2 = function (value) {
    this.u2_1 = this.u2_1 + toString_0(value);
    return this;
  };
  protoOf(StringBuilder).f3 = function (value) {
    this.u2_1 = this.u2_1 + toString_1(value);
    return this;
  };
  protoOf(StringBuilder).v2 = function (value) {
    this.u2_1 = this.u2_1 + toString_1(value);
    return this;
  };
  protoOf(StringBuilder).w2 = function (value) {
    var tmp = this;
    var tmp_0 = this.u2_1;
    tmp.u2_1 = tmp_0 + (value == null ? 'null' : value);
    return this;
  };
  protoOf(StringBuilder).toString = function () {
    return this.u2_1;
  };
  function toString(_this__u8e3s4, radix) {
    // Inline function 'kotlin.js.asDynamic' call
    return _this__u8e3s4.toString(checkRadix(radix));
  }
  function checkRadix(radix) {
    if (!(2 <= radix ? radix <= 36 : false)) {
      throw IllegalArgumentException_init_$Create$_0('radix ' + radix + ' was not in valid range 2..36');
    }
    return radix;
  }
  function Regex_init_$Init$(pattern, $this) {
    Regex.call($this, pattern, emptySet());
    return $this;
  }
  function Regex_init_$Create$(pattern) {
    return Regex_init_$Init$(pattern, objectCreate(protoOf(Regex)));
  }
  function Companion_1() {
    Companion_instance_1 = this;
    this.g3_1 = new RegExp('[\\\\^$*+?.()|[\\]{}]', 'g');
    this.h3_1 = new RegExp('[\\\\$]', 'g');
    this.i3_1 = new RegExp('\\$', 'g');
  }
  var Companion_instance_1;
  function Companion_getInstance_1() {
    if (Companion_instance_1 == null)
      new Companion_1();
    return Companion_instance_1;
  }
  function Regex(pattern, options) {
    Companion_getInstance_1();
    this.j3_1 = pattern;
    this.k3_1 = toSet(options);
    this.l3_1 = new RegExp(pattern, toFlags(options, 'gu'));
    this.m3_1 = null;
    this.n3_1 = null;
  }
  protoOf(Regex).o3 = function (input) {
    reset(this.l3_1);
    var match = this.l3_1.exec(toString_2(input));
    return (!(match == null) ? match.index === 0 : false) ? this.l3_1.lastIndex === charSequenceLength(input) : false;
  };
  protoOf(Regex).toString = function () {
    return this.l3_1.toString();
  };
  function toFlags(_this__u8e3s4, prepend) {
    return joinToString_0(_this__u8e3s4, '', prepend, VOID, VOID, VOID, toFlags$lambda);
  }
  function toFlags$lambda(it) {
    return it.r3_1;
  }
  function toCollection(_this__u8e3s4, destination) {
    var inductionVariable = 0;
    var last = _this__u8e3s4.length;
    while (inductionVariable < last) {
      var item = _this__u8e3s4[inductionVariable];
      inductionVariable = inductionVariable + 1 | 0;
      destination.h(item);
    }
    return destination;
  }
  function indexOf(_this__u8e3s4, element) {
    if (element == null) {
      var inductionVariable = 0;
      var last = _this__u8e3s4.length - 1 | 0;
      if (inductionVariable <= last)
        do {
          var index = inductionVariable;
          inductionVariable = inductionVariable + 1 | 0;
          if (_this__u8e3s4[index] == null) {
            return index;
          }
        }
         while (inductionVariable <= last);
    } else {
      var inductionVariable_0 = 0;
      var last_0 = _this__u8e3s4.length - 1 | 0;
      if (inductionVariable_0 <= last_0)
        do {
          var index_0 = inductionVariable_0;
          inductionVariable_0 = inductionVariable_0 + 1 | 0;
          if (equals(element, _this__u8e3s4[index_0])) {
            return index_0;
          }
        }
         while (inductionVariable_0 <= last_0);
    }
    return -1;
  }
  function joinToString(_this__u8e3s4, separator, prefix, postfix, limit, truncated, transform) {
    separator = separator === VOID ? ', ' : separator;
    prefix = prefix === VOID ? '' : prefix;
    postfix = postfix === VOID ? '' : postfix;
    limit = limit === VOID ? -1 : limit;
    truncated = truncated === VOID ? '...' : truncated;
    transform = transform === VOID ? null : transform;
    return joinTo(_this__u8e3s4, StringBuilder_init_$Create$_0(), separator, prefix, postfix, limit, truncated, transform).toString();
  }
  function joinTo(_this__u8e3s4, buffer, separator, prefix, postfix, limit, truncated, transform) {
    separator = separator === VOID ? ', ' : separator;
    prefix = prefix === VOID ? '' : prefix;
    postfix = postfix === VOID ? '' : postfix;
    limit = limit === VOID ? -1 : limit;
    truncated = truncated === VOID ? '...' : truncated;
    transform = transform === VOID ? null : transform;
    buffer.f3(prefix);
    var count = 0;
    var inductionVariable = 0;
    var last = _this__u8e3s4.length;
    $l$loop: while (inductionVariable < last) {
      var element = _this__u8e3s4[inductionVariable];
      inductionVariable = inductionVariable + 1 | 0;
      count = count + 1 | 0;
      if (count > 1) {
        buffer.f3(separator);
      }
      if (limit < 0 ? true : count <= limit) {
        appendElement(buffer, element, transform);
      } else
        break $l$loop;
    }
    if (limit >= 0 ? count > limit : false) {
      buffer.f3(truncated);
    }
    buffer.f3(postfix);
    return buffer;
  }
  function joinToString_0(_this__u8e3s4, separator, prefix, postfix, limit, truncated, transform) {
    separator = separator === VOID ? ', ' : separator;
    prefix = prefix === VOID ? '' : prefix;
    postfix = postfix === VOID ? '' : postfix;
    limit = limit === VOID ? -1 : limit;
    truncated = truncated === VOID ? '...' : truncated;
    transform = transform === VOID ? null : transform;
    return joinTo_0(_this__u8e3s4, StringBuilder_init_$Create$_0(), separator, prefix, postfix, limit, truncated, transform).toString();
  }
  function joinTo_0(_this__u8e3s4, buffer, separator, prefix, postfix, limit, truncated, transform) {
    separator = separator === VOID ? ', ' : separator;
    prefix = prefix === VOID ? '' : prefix;
    postfix = postfix === VOID ? '' : postfix;
    limit = limit === VOID ? -1 : limit;
    truncated = truncated === VOID ? '...' : truncated;
    transform = transform === VOID ? null : transform;
    buffer.f3(prefix);
    var count = 0;
    var tmp0_iterator = _this__u8e3s4.k();
    $l$loop: while (tmp0_iterator.r()) {
      var element = tmp0_iterator.s();
      count = count + 1 | 0;
      if (count > 1) {
        buffer.f3(separator);
      }
      if (limit < 0 ? true : count <= limit) {
        appendElement(buffer, element, transform);
      } else
        break $l$loop;
    }
    if (limit >= 0 ? count > limit : false) {
      buffer.f3(truncated);
    }
    buffer.f3(postfix);
    return buffer;
  }
  function toSet(_this__u8e3s4) {
    if (isInterface(_this__u8e3s4, Collection)) {
      var tmp;
      switch (_this__u8e3s4.j()) {
        case 0:
          tmp = emptySet();
          break;
        case 1:
          var tmp_0;
          if (isInterface(_this__u8e3s4, List)) {
            tmp_0 = _this__u8e3s4.t(0);
          } else {
            tmp_0 = _this__u8e3s4.k().s();
          }

          tmp = setOf(tmp_0);
          break;
        default:
          tmp = toCollection_0(_this__u8e3s4, LinkedHashSet_init_$Create$_0(mapCapacity(_this__u8e3s4.j())));
          break;
      }
      return tmp;
    }
    return optimizeReadOnlySet(toCollection_0(_this__u8e3s4, LinkedHashSet_init_$Create$()));
  }
  function toCollection_0(_this__u8e3s4, destination) {
    var tmp0_iterator = _this__u8e3s4.k();
    while (tmp0_iterator.r()) {
      var item = tmp0_iterator.s();
      destination.h(item);
    }
    return destination;
  }
  function coerceAtLeast(_this__u8e3s4, minimumValue) {
    return _this__u8e3s4 < minimumValue ? minimumValue : _this__u8e3s4;
  }
  function coerceAtMost(_this__u8e3s4, maximumValue) {
    return _this__u8e3s4 > maximumValue ? maximumValue : _this__u8e3s4;
  }
  function _Char___init__impl__6a9atx(value) {
    return value;
  }
  function _get_value__a43j40($this) {
    return $this;
  }
  function _Char___init__impl__6a9atx_0(code) {
    // Inline function 'kotlin.UShort.toInt' call
    var tmp$ret$0 = _UShort___get_data__impl__g0245(code) & 65535;
    return _Char___init__impl__6a9atx(tmp$ret$0);
  }
  function Char__toInt_impl_vasixd($this) {
    return _get_value__a43j40($this);
  }
  function toString_0($this) {
    // Inline function 'kotlin.js.unsafeCast' call
    return String.fromCharCode(_get_value__a43j40($this));
  }
  function Companion_2() {
    Companion_instance_2 = this;
    this.s3_1 = _Char___init__impl__6a9atx(0);
    this.t3_1 = _Char___init__impl__6a9atx(65535);
    this.u3_1 = _Char___init__impl__6a9atx(55296);
    this.v3_1 = _Char___init__impl__6a9atx(56319);
    this.w3_1 = _Char___init__impl__6a9atx(56320);
    this.x3_1 = _Char___init__impl__6a9atx(57343);
    this.y3_1 = _Char___init__impl__6a9atx(55296);
    this.z3_1 = _Char___init__impl__6a9atx(57343);
    this.a4_1 = 2;
    this.b4_1 = 16;
  }
  var Companion_instance_2;
  function Companion_getInstance_2() {
    if (Companion_instance_2 == null)
      new Companion_2();
    return Companion_instance_2;
  }
  function Char() {
  }
  function List() {
  }
  function Collection() {
  }
  function Set() {
  }
  function Entry() {
  }
  function Map_0() {
  }
  function toString_1(_this__u8e3s4) {
    var tmp1_elvis_lhs = _this__u8e3s4 == null ? null : toString_2(_this__u8e3s4);
    return tmp1_elvis_lhs == null ? 'null' : tmp1_elvis_lhs;
  }
  function implement(interfaces) {
    var maxSize = 1;
    var masks = [];
    var inductionVariable = 0;
    var last = interfaces.length;
    while (inductionVariable < last) {
      var i = interfaces[inductionVariable];
      inductionVariable = inductionVariable + 1 | 0;
      var currentSize = maxSize;
      var tmp1_elvis_lhs = i.prototype.$imask$;
      var imask = tmp1_elvis_lhs == null ? i.$imask$ : tmp1_elvis_lhs;
      if (!(imask == null)) {
        masks.push(imask);
        currentSize = imask.length;
      }
      var iid = i.$metadata$.iid;
      var tmp;
      if (iid == null) {
        tmp = null;
      } else {
        // Inline function 'kotlin.let' call
        // Inline function 'kotlin.contracts.contract' call
        // Inline function 'kotlin.js.implement.<anonymous>' call
        tmp = bitMaskWith(iid);
      }
      var iidImask = tmp;
      if (!(iidImask == null)) {
        masks.push(iidImask);
        currentSize = Math.max(currentSize, iidImask.length);
      }
      if (currentSize > maxSize) {
        maxSize = currentSize;
      }
    }
    return compositeBitMask(maxSize, masks);
  }
  function bitMaskWith(activeBit) {
    var numberIndex = activeBit >> 5;
    var intArray = new Int32Array(numberIndex + 1 | 0);
    var positionInNumber = activeBit & 31;
    var numberWithSettledBit = 1 << positionInNumber;
    intArray[numberIndex] = intArray[numberIndex] | numberWithSettledBit;
    return intArray;
  }
  function compositeBitMask(capacity, masks) {
    var tmp = 0;
    var tmp_0 = new Int32Array(capacity);
    while (tmp < capacity) {
      var tmp_1 = tmp;
      var result = 0;
      var inductionVariable = 0;
      var last = masks.length;
      while (inductionVariable < last) {
        var mask = masks[inductionVariable];
        inductionVariable = inductionVariable + 1 | 0;
        if (tmp_1 < mask.length) {
          result = result | mask[tmp_1];
        }
      }
      tmp_0[tmp_1] = result;
      tmp = tmp + 1 | 0;
    }
    return tmp_0;
  }
  function isBitSet(_this__u8e3s4, possibleActiveBit) {
    var numberIndex = possibleActiveBit >> 5;
    if (numberIndex > _this__u8e3s4.length)
      return false;
    var positionInNumber = possibleActiveBit & 31;
    var numberWithSettledBit = 1 << positionInNumber;
    return !((_this__u8e3s4[numberIndex] & numberWithSettledBit) === 0);
  }
  function fillArrayVal(array, initValue) {
    var inductionVariable = 0;
    var last = array.length - 1 | 0;
    if (inductionVariable <= last)
      do {
        var i = inductionVariable;
        inductionVariable = inductionVariable + 1 | 0;
        array[i] = initValue;
      }
       while (!(i === last));
    return array;
  }
  function get_buf() {
    _init_properties_bitUtils_kt__nfcg4k();
    return buf;
  }
  var buf;
  function get_bufFloat64() {
    _init_properties_bitUtils_kt__nfcg4k();
    return bufFloat64;
  }
  var bufFloat64;
  var bufFloat32;
  function get_bufInt32() {
    _init_properties_bitUtils_kt__nfcg4k();
    return bufInt32;
  }
  var bufInt32;
  function get_lowIndex() {
    _init_properties_bitUtils_kt__nfcg4k();
    return lowIndex;
  }
  var lowIndex;
  function get_highIndex() {
    _init_properties_bitUtils_kt__nfcg4k();
    return highIndex;
  }
  var highIndex;
  function getNumberHashCode(obj) {
    _init_properties_bitUtils_kt__nfcg4k();
    // Inline function 'kotlin.js.unsafeCast' call
    // Inline function 'kotlin.js.jsBitwiseOr' call
    // Inline function 'kotlin.js.asDynamic' call
    if ((obj | 0) === obj) {
      return numberToInt(obj);
    }
    get_bufFloat64()[0] = obj;
    return imul(get_bufInt32()[get_highIndex()], 31) + get_bufInt32()[get_lowIndex()] | 0;
  }
  var properties_initialized_bitUtils_kt_i2bo3e;
  function _init_properties_bitUtils_kt__nfcg4k() {
    if (!properties_initialized_bitUtils_kt_i2bo3e) {
      properties_initialized_bitUtils_kt_i2bo3e = true;
      buf = new ArrayBuffer(8);
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      bufFloat64 = new Float64Array(get_buf());
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      bufFloat32 = new Float32Array(get_buf());
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      bufInt32 = new Int32Array(get_buf());
      // Inline function 'kotlin.run' call
      // Inline function 'kotlin.contracts.contract' call
      // Inline function 'kotlin.js.lowIndex.<anonymous>' call
      get_bufFloat64()[0] = -1.0;
      lowIndex = !(get_bufInt32()[0] === 0) ? 1 : 0;
      highIndex = 1 - get_lowIndex() | 0;
    }
  }
  function charSequenceGet(a, index) {
    var tmp;
    if (isString(a)) {
      // Inline function 'kotlin.Char' call
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      var code = a.charCodeAt(index);
      var tmp_0;
      // Inline function 'kotlin.code' call
      Companion_getInstance_2();
      var this_0 = _Char___init__impl__6a9atx(0);
      if (code < Char__toInt_impl_vasixd(this_0)) {
        tmp_0 = true;
      } else {
        // Inline function 'kotlin.code' call
        Companion_getInstance_2();
        var this_1 = _Char___init__impl__6a9atx(65535);
        tmp_0 = code > Char__toInt_impl_vasixd(this_1);
      }
      if (tmp_0) {
        throw IllegalArgumentException_init_$Create$_0('Invalid Char code: ' + code);
      }
      tmp = numberToChar(code);
    } else {
      tmp = a.b(index);
    }
    return tmp;
  }
  function isString(a) {
    return typeof a === 'string';
  }
  function charSequenceLength(a) {
    var tmp;
    if (isString(a)) {
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      tmp = a.length;
    } else {
      tmp = a.a();
    }
    return tmp;
  }
  function charSequenceSubSequence(a, startIndex, endIndex) {
    var tmp;
    if (isString(a)) {
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      tmp = a.substring(startIndex, endIndex);
    } else {
      tmp = a.c(startIndex, endIndex);
    }
    return tmp;
  }
  function arrayToString(array) {
    return joinToString(array, ', ', '[', ']', VOID, VOID, arrayToString$lambda);
  }
  function arrayToString$lambda(it) {
    return toString_2(it);
  }
  function getObjectHashCode(obj) {
    // Inline function 'kotlin.js.jsIn' call
    if (!('kotlinHashCodeValue$' in obj)) {
      var hash = calculateRandomHash();
      var descriptor = new Object();
      descriptor.value = hash;
      descriptor.enumerable = false;
      Object.defineProperty(obj, 'kotlinHashCodeValue$', descriptor);
    }
    // Inline function 'kotlin.js.unsafeCast' call
    return obj['kotlinHashCodeValue$'];
  }
  function calculateRandomHash() {
    // Inline function 'kotlin.js.jsBitwiseOr' call
    return Math.random() * 4.294967296E9 | 0;
  }
  function toString_2(o) {
    var tmp;
    if (o == null) {
      tmp = 'null';
    } else if (isArrayish(o)) {
      tmp = '[...]';
    } else if (!(typeof o.toString === 'function')) {
      tmp = anyToString(o);
    } else {
      // Inline function 'kotlin.js.unsafeCast' call
      tmp = o.toString();
    }
    return tmp;
  }
  function anyToString(o) {
    return Object.prototype.toString.call(o);
  }
  function hashCode(obj) {
    if (obj == null)
      return 0;
    var typeOf = typeof obj;
    var tmp;
    switch (typeOf) {
      case 'object':
        tmp = 'function' === typeof obj.hashCode ? obj.hashCode() : getObjectHashCode(obj);
        break;
      case 'function':
        tmp = getObjectHashCode(obj);
        break;
      case 'number':
        tmp = getNumberHashCode(obj);
        break;
      case 'boolean':
        // Inline function 'kotlin.js.unsafeCast' call

        tmp = getBooleanHashCode(obj);
        break;
      case 'string':
        tmp = getStringHashCode(String(obj));
        break;
      case 'bigint':
        tmp = getBigIntHashCode(obj);
        break;
      case 'symbol':
        tmp = getSymbolHashCode(obj);
        break;
      default:
        tmp = function () {
          throw new Error('Unexpected typeof `' + typeOf + '`');
        }();
        break;
    }
    return tmp;
  }
  function getBooleanHashCode(value) {
    return value ? 1231 : 1237;
  }
  function getStringHashCode(str) {
    var hash = 0;
    var length = str.length;
    var inductionVariable = 0;
    var last = length - 1 | 0;
    if (inductionVariable <= last)
      do {
        var i = inductionVariable;
        inductionVariable = inductionVariable + 1 | 0;
        // Inline function 'kotlin.js.asDynamic' call
        var code = str.charCodeAt(i);
        hash = imul(hash, 31) + code | 0;
      }
       while (!(i === last));
    return hash;
  }
  function getBigIntHashCode(value) {
    var shiftNumber = BigInt(32);
    var MASK = BigInt(4.294967295E9);
    var bigNumber = value < 0 ? -value : value;
    var hashCode = 0;
    var signum = value < 0 ? -1 : 1;
    while (bigNumber != 0) {
      // Inline function 'kotlin.js.unsafeCast' call
      var chunk = Number(bigNumber & MASK);
      hashCode = imul(31, hashCode) + chunk | 0;
      bigNumber = bigNumber >> shiftNumber;
    }
    return imul(hashCode, signum);
  }
  function getSymbolHashCode(value) {
    var hashCodeMap = symbolIsSharable(value) ? getSymbolMap() : getSymbolWeakMap();
    var cachedHashCode = hashCodeMap.get(value);
    if (cachedHashCode !== VOID)
      return cachedHashCode;
    var hash = calculateRandomHash();
    hashCodeMap.set(value, hash);
    return hash;
  }
  function symbolIsSharable(symbol) {
    return Symbol.keyFor(symbol) != VOID;
  }
  function getSymbolMap() {
    if (symbolMap === VOID) {
      symbolMap = new Map();
    }
    return symbolMap;
  }
  function getSymbolWeakMap() {
    if (symbolWeakMap === VOID) {
      symbolWeakMap = new WeakMap();
    }
    return symbolWeakMap;
  }
  var symbolMap;
  var symbolWeakMap;
  function equals(obj1, obj2) {
    if (obj1 == null) {
      return obj2 == null;
    }
    if (obj2 == null) {
      return false;
    }
    if (typeof obj1 === 'object' ? typeof obj1.equals === 'function' : false) {
      return obj1.equals(obj2);
    }
    if (obj1 !== obj1) {
      return obj2 !== obj2;
    }
    if (typeof obj1 === 'number' ? typeof obj2 === 'number' : false) {
      var tmp;
      if (obj1 === obj2) {
        var tmp_0;
        if (obj1 !== 0) {
          tmp_0 = true;
        } else {
          // Inline function 'kotlin.js.asDynamic' call
          var tmp_1 = 1 / obj1;
          // Inline function 'kotlin.js.asDynamic' call
          tmp_0 = tmp_1 === 1 / obj2;
        }
        tmp = tmp_0;
      } else {
        tmp = false;
      }
      return tmp;
    }
    return obj1 === obj2;
  }
  function unboxIntrinsic(x) {
    var message = 'Should be lowered';
    throw IllegalStateException_init_$Create$_0(toString_2(message));
  }
  function captureStack(instance, constructorFunction) {
    if (Error.captureStackTrace != null) {
      Error.captureStackTrace(instance, constructorFunction);
    } else {
      // Inline function 'kotlin.js.asDynamic' call
      instance.stack = (new Error()).stack;
    }
  }
  function protoOf(constructor) {
    return constructor.prototype;
  }
  function defineProp(obj, name, getter, setter) {
    return Object.defineProperty(obj, name, {configurable: true, get: getter, set: setter});
  }
  function objectCreate(proto) {
    return Object.create(proto);
  }
  function extendThrowable(this_, message, cause) {
    Error.call(this_);
    setPropertiesToThrowableInstance(this_, message, cause);
  }
  function setPropertiesToThrowableInstance(this_, message, cause) {
    var errorInfo = calculateErrorInfo(Object.getPrototypeOf(this_));
    if ((errorInfo & 1) === 0) {
      var tmp;
      if (message == null) {
        var tmp_0;
        if (!(message === null)) {
          var tmp1_elvis_lhs = cause == null ? null : cause.toString();
          tmp_0 = tmp1_elvis_lhs == null ? VOID : tmp1_elvis_lhs;
        } else {
          tmp_0 = VOID;
        }
        tmp = tmp_0;
      } else {
        tmp = message;
      }
      this_.message = tmp;
    }
    if ((errorInfo & 2) === 0) {
      this_.cause = cause;
    }
    this_.name = Object.getPrototypeOf(this_).constructor.name;
  }
  function ensureNotNull(v) {
    var tmp;
    if (v == null) {
      THROW_NPE();
    } else {
      tmp = v;
    }
    return tmp;
  }
  function THROW_NPE() {
    throw NullPointerException_init_$Create$();
  }
  function THROW_CCE() {
    throw ClassCastException_init_$Create$();
  }
  function fillFrom(src, dst) {
    var srcLen = src.length;
    var dstLen = dst.length;
    var index = 0;
    // Inline function 'kotlin.js.unsafeCast' call
    var arr = dst;
    while (index < srcLen ? index < dstLen : false) {
      var tmp = index;
      var tmp0 = index;
      index = tmp0 + 1 | 0;
      arr[tmp] = src[tmp0];
    }
    return dst;
  }
  function arrayCopyResize(source, newSize, defaultValue) {
    // Inline function 'kotlin.js.unsafeCast' call
    var result = source.slice(0, newSize);
    // Inline function 'kotlin.copyArrayType' call
    if (source.$type$ !== undefined) {
      result.$type$ = source.$type$;
    }
    var index = source.length;
    if (newSize > index) {
      // Inline function 'kotlin.js.asDynamic' call
      result.length = newSize;
      while (index < newSize) {
        var tmp0 = index;
        index = tmp0 + 1 | 0;
        result[tmp0] = defaultValue;
      }
    }
    return result;
  }
  function Companion_3() {
    Companion_instance_3 = this;
    this.c4_1 = new Long(0, -2147483648);
    this.d4_1 = new Long(-1, 2147483647);
    this.e4_1 = 8;
    this.f4_1 = 64;
  }
  var Companion_instance_3;
  function Companion_getInstance_3() {
    if (Companion_instance_3 == null)
      new Companion_3();
    return Companion_instance_3;
  }
  function Long(low, high) {
    Companion_getInstance_3();
    Number_0.call(this);
    this.g4_1 = low;
    this.h4_1 = high;
  }
  protoOf(Long).i4 = function (other) {
    return add(this, other);
  };
  protoOf(Long).j4 = function (other) {
    return divide(this, other);
  };
  protoOf(Long).k4 = function () {
    return this.l4().i4(new Long(1, 0));
  };
  protoOf(Long).l4 = function () {
    return new Long(~this.g4_1, ~this.h4_1);
  };
  protoOf(Long).m4 = function () {
    return this.g4_1;
  };
  protoOf(Long).n4 = function () {
    return toNumber(this);
  };
  protoOf(Long).valueOf = function () {
    return this.n4();
  };
  protoOf(Long).equals = function (other) {
    var tmp;
    if (other instanceof Long) {
      tmp = equalsLong(this, other);
    } else {
      tmp = false;
    }
    return tmp;
  };
  protoOf(Long).hashCode = function () {
    return hashCode_0(this);
  };
  protoOf(Long).toString = function () {
    return toStringImpl(this, 10);
  };
  function get_ZERO() {
    _init_properties_longjs_kt__tqrzid();
    return ZERO;
  }
  var ZERO;
  function get_ONE() {
    _init_properties_longjs_kt__tqrzid();
    return ONE;
  }
  var ONE;
  function get_NEG_ONE() {
    _init_properties_longjs_kt__tqrzid();
    return NEG_ONE;
  }
  var NEG_ONE;
  function get_MAX_VALUE() {
    _init_properties_longjs_kt__tqrzid();
    return MAX_VALUE;
  }
  var MAX_VALUE;
  function get_MIN_VALUE() {
    _init_properties_longjs_kt__tqrzid();
    return MIN_VALUE;
  }
  var MIN_VALUE;
  function get_TWO_PWR_24_() {
    _init_properties_longjs_kt__tqrzid();
    return TWO_PWR_24_;
  }
  var TWO_PWR_24_;
  function compare(_this__u8e3s4, other) {
    _init_properties_longjs_kt__tqrzid();
    if (equalsLong(_this__u8e3s4, other)) {
      return 0;
    }
    var thisNeg = isNegative(_this__u8e3s4);
    var otherNeg = isNegative(other);
    return (thisNeg ? !otherNeg : false) ? -1 : (!thisNeg ? otherNeg : false) ? 1 : isNegative(subtract(_this__u8e3s4, other)) ? -1 : 1;
  }
  function add(_this__u8e3s4, other) {
    _init_properties_longjs_kt__tqrzid();
    var a48 = _this__u8e3s4.h4_1 >>> 16 | 0;
    var a32 = _this__u8e3s4.h4_1 & 65535;
    var a16 = _this__u8e3s4.g4_1 >>> 16 | 0;
    var a00 = _this__u8e3s4.g4_1 & 65535;
    var b48 = other.h4_1 >>> 16 | 0;
    var b32 = other.h4_1 & 65535;
    var b16 = other.g4_1 >>> 16 | 0;
    var b00 = other.g4_1 & 65535;
    var c48 = 0;
    var c32 = 0;
    var c16 = 0;
    var c00 = 0;
    c00 = c00 + (a00 + b00 | 0) | 0;
    c16 = c16 + (c00 >>> 16 | 0) | 0;
    c00 = c00 & 65535;
    c16 = c16 + (a16 + b16 | 0) | 0;
    c32 = c32 + (c16 >>> 16 | 0) | 0;
    c16 = c16 & 65535;
    c32 = c32 + (a32 + b32 | 0) | 0;
    c48 = c48 + (c32 >>> 16 | 0) | 0;
    c32 = c32 & 65535;
    c48 = c48 + (a48 + b48 | 0) | 0;
    c48 = c48 & 65535;
    return new Long(c16 << 16 | c00, c48 << 16 | c32);
  }
  function subtract(_this__u8e3s4, other) {
    _init_properties_longjs_kt__tqrzid();
    return add(_this__u8e3s4, other.k4());
  }
  function multiply(_this__u8e3s4, other) {
    _init_properties_longjs_kt__tqrzid();
    if (isZero(_this__u8e3s4)) {
      return get_ZERO();
    } else if (isZero(other)) {
      return get_ZERO();
    }
    if (equalsLong(_this__u8e3s4, get_MIN_VALUE())) {
      return isOdd(other) ? get_MIN_VALUE() : get_ZERO();
    } else if (equalsLong(other, get_MIN_VALUE())) {
      return isOdd(_this__u8e3s4) ? get_MIN_VALUE() : get_ZERO();
    }
    if (isNegative(_this__u8e3s4)) {
      var tmp;
      if (isNegative(other)) {
        tmp = multiply(negate(_this__u8e3s4), negate(other));
      } else {
        tmp = negate(multiply(negate(_this__u8e3s4), other));
      }
      return tmp;
    } else if (isNegative(other)) {
      return negate(multiply(_this__u8e3s4, negate(other)));
    }
    if (lessThan(_this__u8e3s4, get_TWO_PWR_24_()) ? lessThan(other, get_TWO_PWR_24_()) : false) {
      return fromNumber(toNumber(_this__u8e3s4) * toNumber(other));
    }
    var a48 = _this__u8e3s4.h4_1 >>> 16 | 0;
    var a32 = _this__u8e3s4.h4_1 & 65535;
    var a16 = _this__u8e3s4.g4_1 >>> 16 | 0;
    var a00 = _this__u8e3s4.g4_1 & 65535;
    var b48 = other.h4_1 >>> 16 | 0;
    var b32 = other.h4_1 & 65535;
    var b16 = other.g4_1 >>> 16 | 0;
    var b00 = other.g4_1 & 65535;
    var c48 = 0;
    var c32 = 0;
    var c16 = 0;
    var c00 = 0;
    c00 = c00 + imul(a00, b00) | 0;
    c16 = c16 + (c00 >>> 16 | 0) | 0;
    c00 = c00 & 65535;
    c16 = c16 + imul(a16, b00) | 0;
    c32 = c32 + (c16 >>> 16 | 0) | 0;
    c16 = c16 & 65535;
    c16 = c16 + imul(a00, b16) | 0;
    c32 = c32 + (c16 >>> 16 | 0) | 0;
    c16 = c16 & 65535;
    c32 = c32 + imul(a32, b00) | 0;
    c48 = c48 + (c32 >>> 16 | 0) | 0;
    c32 = c32 & 65535;
    c32 = c32 + imul(a16, b16) | 0;
    c48 = c48 + (c32 >>> 16 | 0) | 0;
    c32 = c32 & 65535;
    c32 = c32 + imul(a00, b32) | 0;
    c48 = c48 + (c32 >>> 16 | 0) | 0;
    c32 = c32 & 65535;
    c48 = c48 + (((imul(a48, b00) + imul(a32, b16) | 0) + imul(a16, b32) | 0) + imul(a00, b48) | 0) | 0;
    c48 = c48 & 65535;
    return new Long(c16 << 16 | c00, c48 << 16 | c32);
  }
  function divide(_this__u8e3s4, other) {
    _init_properties_longjs_kt__tqrzid();
    if (isZero(other)) {
      throw Exception_init_$Create$_0('division by zero');
    } else if (isZero(_this__u8e3s4)) {
      return get_ZERO();
    }
    if (equalsLong(_this__u8e3s4, get_MIN_VALUE())) {
      if (equalsLong(other, get_ONE()) ? true : equalsLong(other, get_NEG_ONE())) {
        return get_MIN_VALUE();
      } else if (equalsLong(other, get_MIN_VALUE())) {
        return get_ONE();
      } else {
        var halfThis = shiftRight(_this__u8e3s4, 1);
        var approx = shiftLeft(halfThis.j4(other), 1);
        if (equalsLong(approx, get_ZERO())) {
          return isNegative(other) ? get_ONE() : get_NEG_ONE();
        } else {
          var rem = subtract(_this__u8e3s4, multiply(other, approx));
          return add(approx, rem.j4(other));
        }
      }
    } else if (equalsLong(other, get_MIN_VALUE())) {
      return get_ZERO();
    }
    if (isNegative(_this__u8e3s4)) {
      var tmp;
      if (isNegative(other)) {
        tmp = negate(_this__u8e3s4).j4(negate(other));
      } else {
        tmp = negate(negate(_this__u8e3s4).j4(other));
      }
      return tmp;
    } else if (isNegative(other)) {
      return negate(_this__u8e3s4.j4(negate(other)));
    }
    var res = get_ZERO();
    var rem_0 = _this__u8e3s4;
    while (greaterThanOrEqual(rem_0, other)) {
      var approxDouble = toNumber(rem_0) / toNumber(other);
      var approx2 = Math.max(1.0, Math.floor(approxDouble));
      var log2 = Math.ceil(Math.log(approx2) / Math.LN2);
      var delta = log2 <= 48.0 ? 1.0 : Math.pow(2.0, log2 - 48);
      var approxRes = fromNumber(approx2);
      var approxRem = multiply(approxRes, other);
      while (isNegative(approxRem) ? true : greaterThan(approxRem, rem_0)) {
        approx2 = approx2 - delta;
        approxRes = fromNumber(approx2);
        approxRem = multiply(approxRes, other);
      }
      if (isZero(approxRes)) {
        approxRes = get_ONE();
      }
      res = add(res, approxRes);
      rem_0 = subtract(rem_0, approxRem);
    }
    return res;
  }
  function shiftLeft(_this__u8e3s4, numBits) {
    _init_properties_longjs_kt__tqrzid();
    var numBits_0 = numBits & 63;
    if (numBits_0 === 0) {
      return _this__u8e3s4;
    } else {
      if (numBits_0 < 32) {
        return new Long(_this__u8e3s4.g4_1 << numBits_0, _this__u8e3s4.h4_1 << numBits_0 | (_this__u8e3s4.g4_1 >>> (32 - numBits_0 | 0) | 0));
      } else {
        return new Long(0, _this__u8e3s4.g4_1 << (numBits_0 - 32 | 0));
      }
    }
  }
  function shiftRight(_this__u8e3s4, numBits) {
    _init_properties_longjs_kt__tqrzid();
    var numBits_0 = numBits & 63;
    if (numBits_0 === 0) {
      return _this__u8e3s4;
    } else {
      if (numBits_0 < 32) {
        return new Long(_this__u8e3s4.g4_1 >>> numBits_0 | 0 | _this__u8e3s4.h4_1 << (32 - numBits_0 | 0), _this__u8e3s4.h4_1 >> numBits_0);
      } else {
        return new Long(_this__u8e3s4.h4_1 >> (numBits_0 - 32 | 0), _this__u8e3s4.h4_1 >= 0 ? 0 : -1);
      }
    }
  }
  function toNumber(_this__u8e3s4) {
    _init_properties_longjs_kt__tqrzid();
    return _this__u8e3s4.h4_1 * 4.294967296E9 + getLowBitsUnsigned(_this__u8e3s4);
  }
  function equalsLong(_this__u8e3s4, other) {
    _init_properties_longjs_kt__tqrzid();
    return _this__u8e3s4.h4_1 === other.h4_1 ? _this__u8e3s4.g4_1 === other.g4_1 : false;
  }
  function hashCode_0(l) {
    _init_properties_longjs_kt__tqrzid();
    return l.g4_1 ^ l.h4_1;
  }
  function toStringImpl(_this__u8e3s4, radix) {
    _init_properties_longjs_kt__tqrzid();
    if (radix < 2 ? true : 36 < radix) {
      throw Exception_init_$Create$_0('radix out of range: ' + radix);
    }
    if (isZero(_this__u8e3s4)) {
      return '0';
    }
    if (isNegative(_this__u8e3s4)) {
      if (equalsLong(_this__u8e3s4, get_MIN_VALUE())) {
        var radixLong = fromInt(radix);
        var div = _this__u8e3s4.j4(radixLong);
        var rem = subtract(multiply(div, radixLong), _this__u8e3s4).m4();
        var tmp = toStringImpl(div, radix);
        // Inline function 'kotlin.js.unsafeCast' call
        // Inline function 'kotlin.js.asDynamic' call
        return tmp + rem.toString(radix);
      } else {
        return '-' + toStringImpl(negate(_this__u8e3s4), radix);
      }
    }
    var digitsPerTime = radix === 2 ? 31 : radix <= 10 ? 9 : radix <= 21 ? 7 : radix <= 35 ? 6 : 5;
    var radixToPower = fromNumber(Math.pow(radix, digitsPerTime));
    var rem_0 = _this__u8e3s4;
    var result = '';
    while (true) {
      var remDiv = rem_0.j4(radixToPower);
      var intval = subtract(rem_0, multiply(remDiv, radixToPower)).m4();
      // Inline function 'kotlin.js.unsafeCast' call
      // Inline function 'kotlin.js.asDynamic' call
      var digits = intval.toString(radix);
      rem_0 = remDiv;
      if (isZero(rem_0)) {
        return digits + result;
      } else {
        while (digits.length < digitsPerTime) {
          digits = '0' + digits;
        }
        result = digits + result;
      }
    }
  }
  function fromInt(value) {
    _init_properties_longjs_kt__tqrzid();
    return new Long(value, value < 0 ? -1 : 0);
  }
  function isNegative(_this__u8e3s4) {
    _init_properties_longjs_kt__tqrzid();
    return _this__u8e3s4.h4_1 < 0;
  }
  function isZero(_this__u8e3s4) {
    _init_properties_longjs_kt__tqrzid();
    return _this__u8e3s4.h4_1 === 0 ? _this__u8e3s4.g4_1 === 0 : false;
  }
  function isOdd(_this__u8e3s4) {
    _init_properties_longjs_kt__tqrzid();
    return (_this__u8e3s4.g4_1 & 1) === 1;
  }
  function negate(_this__u8e3s4) {
    _init_properties_longjs_kt__tqrzid();
    return _this__u8e3s4.k4();
  }
  function lessThan(_this__u8e3s4, other) {
    _init_properties_longjs_kt__tqrzid();
    return compare(_this__u8e3s4, other) < 0;
  }
  function fromNumber(value) {
    _init_properties_longjs_kt__tqrzid();
    if (isNaN_0(value)) {
      return get_ZERO();
    } else if (value <= -9.223372036854776E18) {
      return get_MIN_VALUE();
    } else if (value + 1 >= 9.223372036854776E18) {
      return get_MAX_VALUE();
    } else if (value < 0.0) {
      return negate(fromNumber(-value));
    } else {
      var twoPwr32 = 4.294967296E9;
      // Inline function 'kotlin.js.jsBitwiseOr' call
      var tmp = value % twoPwr32 | 0;
      // Inline function 'kotlin.js.jsBitwiseOr' call
      var tmp$ret$1 = value / twoPwr32 | 0;
      return new Long(tmp, tmp$ret$1);
    }
  }
  function greaterThan(_this__u8e3s4, other) {
    _init_properties_longjs_kt__tqrzid();
    return compare(_this__u8e3s4, other) > 0;
  }
  function greaterThanOrEqual(_this__u8e3s4, other) {
    _init_properties_longjs_kt__tqrzid();
    return compare(_this__u8e3s4, other) >= 0;
  }
  function getLowBitsUnsigned(_this__u8e3s4) {
    _init_properties_longjs_kt__tqrzid();
    return _this__u8e3s4.g4_1 >= 0 ? _this__u8e3s4.g4_1 : 4.294967296E9 + _this__u8e3s4.g4_1;
  }
  var properties_initialized_longjs_kt_5aju7t;
  function _init_properties_longjs_kt__tqrzid() {
    if (!properties_initialized_longjs_kt_5aju7t) {
      properties_initialized_longjs_kt_5aju7t = true;
      ZERO = fromInt(0);
      ONE = fromInt(1);
      NEG_ONE = fromInt(-1);
      MAX_VALUE = new Long(-1, 2147483647);
      MIN_VALUE = new Long(0, -2147483648);
      TWO_PWR_24_ = fromInt(16777216);
    }
  }
  function classMeta(name, defaultConstructor, associatedObjectKey, associatedObjects, suspendArity) {
    return createMetadata('class', name, defaultConstructor, associatedObjectKey, associatedObjects, suspendArity, null);
  }
  function createMetadata(kind, name, defaultConstructor, associatedObjectKey, associatedObjects, suspendArity, iid) {
    var undef = VOID;
    return {kind: kind, simpleName: name, associatedObjectKey: associatedObjectKey, associatedObjects: associatedObjects, suspendArity: suspendArity, $kClass$: undef, defaultConstructor: defaultConstructor, iid: iid};
  }
  function setMetadataFor(ctor, name, metadataConstructor, parent, interfaces, defaultConstructor, associatedObjectKey, associatedObjects, suspendArity) {
    if (!(parent == null)) {
      ctor.prototype = Object.create(parent.prototype);
      ctor.prototype.constructor = ctor;
    }
    var metadata = metadataConstructor(name, defaultConstructor, associatedObjectKey, associatedObjects, suspendArity == null ? [] : suspendArity);
    ctor.$metadata$ = metadata;
    if (!(interfaces == null)) {
      var receiver = !(metadata.iid == null) ? ctor : ctor.prototype;
      receiver.$imask$ = implement(interfaces);
    }
  }
  function interfaceMeta(name, defaultConstructor, associatedObjectKey, associatedObjects, suspendArity) {
    return createMetadata('interface', name, defaultConstructor, associatedObjectKey, associatedObjects, suspendArity, generateInterfaceId());
  }
  function generateInterfaceId() {
    if (iid === VOID) {
      iid = 0;
    }
    // Inline function 'kotlin.js.unsafeCast' call
    iid = iid + 1 | 0;
    // Inline function 'kotlin.js.unsafeCast' call
    return iid;
  }
  var iid;
  function objectMeta(name, defaultConstructor, associatedObjectKey, associatedObjects, suspendArity) {
    return createMetadata('object', name, defaultConstructor, associatedObjectKey, associatedObjects, suspendArity, null);
  }
  function numberToInt(a) {
    var tmp;
    if (a instanceof Long) {
      tmp = a.m4();
    } else {
      tmp = doubleToInt(a);
    }
    return tmp;
  }
  function doubleToInt(a) {
    var tmp;
    if (a > 2.147483647E9) {
      tmp = 2147483647;
    } else if (a < -2.147483648E9) {
      tmp = -2147483648;
    } else {
      // Inline function 'kotlin.js.jsBitwiseOr' call
      tmp = a | 0;
    }
    return tmp;
  }
  function toShort(a) {
    // Inline function 'kotlin.js.unsafeCast' call
    return a << 16 >> 16;
  }
  function numberToChar(a) {
    // Inline function 'kotlin.toUShort' call
    var this_0 = numberToInt(a);
    var tmp$ret$0 = _UShort___init__impl__jigrne(toShort(this_0));
    return _Char___init__impl__6a9atx_0(tmp$ret$0);
  }
  function isArrayish(o) {
    return isJsArray(o) ? true : isView(o);
  }
  function isJsArray(obj) {
    // Inline function 'kotlin.js.unsafeCast' call
    return Array.isArray(obj);
  }
  function isInterface(obj, iface) {
    return isInterfaceImpl(obj, iface.$metadata$.iid);
  }
  function isInterfaceImpl(obj, iface) {
    // Inline function 'kotlin.js.unsafeCast' call
    var tmp0_elvis_lhs = obj.$imask$;
    var tmp;
    if (tmp0_elvis_lhs == null) {
      return false;
    } else {
      tmp = tmp0_elvis_lhs;
    }
    var mask = tmp;
    return isBitSet(mask, iface);
  }
  function isCharSequence(value) {
    return typeof value === 'string' ? true : isInterface(value, CharSequence);
  }
  function calculateErrorInfo(proto) {
    var tmp0_safe_receiver = proto.constructor;
    var metadata = tmp0_safe_receiver == null ? null : tmp0_safe_receiver.$metadata$;
    var tmp2_safe_receiver = metadata == null ? null : metadata.errorInfo;
    if (tmp2_safe_receiver == null)
      null;
    else {
      // Inline function 'kotlin.let' call
      // Inline function 'kotlin.contracts.contract' call
      return tmp2_safe_receiver;
    }
    var result = 0;
    if (hasProp(proto, 'message'))
      result = result | 1;
    if (hasProp(proto, 'cause'))
      result = result | 2;
    if (!(result === 3)) {
      var parentProto = getPrototypeOf(proto);
      if (parentProto != Error.prototype) {
        result = result | calculateErrorInfo(parentProto);
      }
    }
    if (!(metadata == null)) {
      metadata.errorInfo = result;
    }
    return result;
  }
  function hasProp(proto, propName) {
    return proto.hasOwnProperty(propName);
  }
  function getPrototypeOf(obj) {
    return Object.getPrototypeOf(obj);
  }
  function get_VOID() {
    _init_properties_void_kt__3zg9as();
    return VOID;
  }
  var VOID;
  var properties_initialized_void_kt_e4ret2;
  function _init_properties_void_kt__3zg9as() {
    if (!properties_initialized_void_kt_e4ret2) {
      properties_initialized_void_kt_e4ret2 = true;
      VOID = void 0;
    }
  }
  function fill(_this__u8e3s4, element, fromIndex, toIndex) {
    fromIndex = fromIndex === VOID ? 0 : fromIndex;
    toIndex = toIndex === VOID ? _this__u8e3s4.length : toIndex;
    Companion_instance_4.o4(fromIndex, toIndex, _this__u8e3s4.length);
    // Inline function 'kotlin.js.nativeFill' call
    // Inline function 'kotlin.js.asDynamic' call
    _this__u8e3s4.fill(element, fromIndex, toIndex);
  }
  function asList(_this__u8e3s4) {
    // Inline function 'kotlin.js.unsafeCast' call
    // Inline function 'kotlin.js.asDynamic' call
    return new ArrayList(_this__u8e3s4);
  }
  function copyOf(_this__u8e3s4, newSize) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(newSize >= 0)) {
      // Inline function 'kotlin.collections.copyOf.<anonymous>' call
      var message = 'Invalid new array size: ' + newSize + '.';
      throw IllegalArgumentException_init_$Create$_0(toString_2(message));
    }
    return fillFrom(_this__u8e3s4, new Int32Array(newSize));
  }
  function copyOf_0(_this__u8e3s4, newSize) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(newSize >= 0)) {
      // Inline function 'kotlin.collections.copyOf.<anonymous>' call
      var message = 'Invalid new array size: ' + newSize + '.';
      throw IllegalArgumentException_init_$Create$_0(toString_2(message));
    }
    return arrayCopyResize(_this__u8e3s4, newSize, null);
  }
  function Exception_init_$Init$($this) {
    extendThrowable($this);
    Exception.call($this);
    return $this;
  }
  function Exception_init_$Create$() {
    var tmp = Exception_init_$Init$(objectCreate(protoOf(Exception)));
    captureStack(tmp, Exception_init_$Create$);
    return tmp;
  }
  function Exception_init_$Init$_0(message, $this) {
    extendThrowable($this, message);
    Exception.call($this);
    return $this;
  }
  function Exception_init_$Create$_0(message) {
    var tmp = Exception_init_$Init$_0(message, objectCreate(protoOf(Exception)));
    captureStack(tmp, Exception_init_$Create$_0);
    return tmp;
  }
  function Exception() {
    captureStack(this, Exception);
  }
  function IllegalArgumentException_init_$Init$($this) {
    RuntimeException_init_$Init$($this);
    IllegalArgumentException.call($this);
    return $this;
  }
  function IllegalArgumentException_init_$Create$() {
    var tmp = IllegalArgumentException_init_$Init$(objectCreate(protoOf(IllegalArgumentException)));
    captureStack(tmp, IllegalArgumentException_init_$Create$);
    return tmp;
  }
  function IllegalArgumentException_init_$Init$_0(message, $this) {
    RuntimeException_init_$Init$_0(message, $this);
    IllegalArgumentException.call($this);
    return $this;
  }
  function IllegalArgumentException_init_$Create$_0(message) {
    var tmp = IllegalArgumentException_init_$Init$_0(message, objectCreate(protoOf(IllegalArgumentException)));
    captureStack(tmp, IllegalArgumentException_init_$Create$_0);
    return tmp;
  }
  function IllegalArgumentException() {
    captureStack(this, IllegalArgumentException);
  }
  function IndexOutOfBoundsException_init_$Init$($this) {
    RuntimeException_init_$Init$($this);
    IndexOutOfBoundsException.call($this);
    return $this;
  }
  function IndexOutOfBoundsException_init_$Create$() {
    var tmp = IndexOutOfBoundsException_init_$Init$(objectCreate(protoOf(IndexOutOfBoundsException)));
    captureStack(tmp, IndexOutOfBoundsException_init_$Create$);
    return tmp;
  }
  function IndexOutOfBoundsException_init_$Init$_0(message, $this) {
    RuntimeException_init_$Init$_0(message, $this);
    IndexOutOfBoundsException.call($this);
    return $this;
  }
  function IndexOutOfBoundsException_init_$Create$_0(message) {
    var tmp = IndexOutOfBoundsException_init_$Init$_0(message, objectCreate(protoOf(IndexOutOfBoundsException)));
    captureStack(tmp, IndexOutOfBoundsException_init_$Create$_0);
    return tmp;
  }
  function IndexOutOfBoundsException() {
    captureStack(this, IndexOutOfBoundsException);
  }
  function IllegalStateException_init_$Init$($this) {
    RuntimeException_init_$Init$($this);
    IllegalStateException.call($this);
    return $this;
  }
  function IllegalStateException_init_$Create$() {
    var tmp = IllegalStateException_init_$Init$(objectCreate(protoOf(IllegalStateException)));
    captureStack(tmp, IllegalStateException_init_$Create$);
    return tmp;
  }
  function IllegalStateException_init_$Init$_0(message, $this) {
    RuntimeException_init_$Init$_0(message, $this);
    IllegalStateException.call($this);
    return $this;
  }
  function IllegalStateException_init_$Create$_0(message) {
    var tmp = IllegalStateException_init_$Init$_0(message, objectCreate(protoOf(IllegalStateException)));
    captureStack(tmp, IllegalStateException_init_$Create$_0);
    return tmp;
  }
  function IllegalStateException() {
    captureStack(this, IllegalStateException);
  }
  function UnsupportedOperationException_init_$Init$($this) {
    RuntimeException_init_$Init$($this);
    UnsupportedOperationException.call($this);
    return $this;
  }
  function UnsupportedOperationException_init_$Create$() {
    var tmp = UnsupportedOperationException_init_$Init$(objectCreate(protoOf(UnsupportedOperationException)));
    captureStack(tmp, UnsupportedOperationException_init_$Create$);
    return tmp;
  }
  function UnsupportedOperationException() {
    captureStack(this, UnsupportedOperationException);
  }
  function RuntimeException_init_$Init$($this) {
    Exception_init_$Init$($this);
    RuntimeException.call($this);
    return $this;
  }
  function RuntimeException_init_$Create$() {
    var tmp = RuntimeException_init_$Init$(objectCreate(protoOf(RuntimeException)));
    captureStack(tmp, RuntimeException_init_$Create$);
    return tmp;
  }
  function RuntimeException_init_$Init$_0(message, $this) {
    Exception_init_$Init$_0(message, $this);
    RuntimeException.call($this);
    return $this;
  }
  function RuntimeException_init_$Create$_0(message) {
    var tmp = RuntimeException_init_$Init$_0(message, objectCreate(protoOf(RuntimeException)));
    captureStack(tmp, RuntimeException_init_$Create$_0);
    return tmp;
  }
  function RuntimeException() {
    captureStack(this, RuntimeException);
  }
  function NoSuchElementException_init_$Init$($this) {
    RuntimeException_init_$Init$($this);
    NoSuchElementException.call($this);
    return $this;
  }
  function NoSuchElementException_init_$Create$() {
    var tmp = NoSuchElementException_init_$Init$(objectCreate(protoOf(NoSuchElementException)));
    captureStack(tmp, NoSuchElementException_init_$Create$);
    return tmp;
  }
  function NoSuchElementException() {
    captureStack(this, NoSuchElementException);
  }
  function ConcurrentModificationException_init_$Init$($this) {
    RuntimeException_init_$Init$($this);
    ConcurrentModificationException.call($this);
    return $this;
  }
  function ConcurrentModificationException_init_$Create$() {
    var tmp = ConcurrentModificationException_init_$Init$(objectCreate(protoOf(ConcurrentModificationException)));
    captureStack(tmp, ConcurrentModificationException_init_$Create$);
    return tmp;
  }
  function ConcurrentModificationException() {
    captureStack(this, ConcurrentModificationException);
  }
  function NullPointerException_init_$Init$($this) {
    RuntimeException_init_$Init$($this);
    NullPointerException.call($this);
    return $this;
  }
  function NullPointerException_init_$Create$() {
    var tmp = NullPointerException_init_$Init$(objectCreate(protoOf(NullPointerException)));
    captureStack(tmp, NullPointerException_init_$Create$);
    return tmp;
  }
  function NullPointerException() {
    captureStack(this, NullPointerException);
  }
  function ClassCastException_init_$Init$($this) {
    RuntimeException_init_$Init$($this);
    ClassCastException.call($this);
    return $this;
  }
  function ClassCastException_init_$Create$() {
    var tmp = ClassCastException_init_$Init$(objectCreate(protoOf(ClassCastException)));
    captureStack(tmp, ClassCastException_init_$Create$);
    return tmp;
  }
  function ClassCastException() {
    captureStack(this, ClassCastException);
  }
  function AbstractCollection$toString$lambda(this$0) {
    return function (it) {
      return it === this$0 ? '(this Collection)' : toString_1(it);
    };
  }
  function AbstractCollection() {
  }
  protoOf(AbstractCollection).l = function (element) {
    var tmp$ret$0;
    $l$block_0: {
      // Inline function 'kotlin.collections.any' call
      var tmp;
      if (isInterface(this, Collection)) {
        tmp = this.n();
      } else {
        tmp = false;
      }
      if (tmp) {
        tmp$ret$0 = false;
        break $l$block_0;
      }
      var tmp0_iterator = this.k();
      while (tmp0_iterator.r()) {
        var element_0 = tmp0_iterator.s();
        // Inline function 'kotlin.collections.AbstractCollection.contains.<anonymous>' call
        if (equals(element_0, element)) {
          tmp$ret$0 = true;
          break $l$block_0;
        }
      }
      tmp$ret$0 = false;
    }
    return tmp$ret$0;
  };
  protoOf(AbstractCollection).m = function (elements) {
    var tmp$ret$0;
    $l$block_0: {
      // Inline function 'kotlin.collections.all' call
      var tmp;
      if (isInterface(elements, Collection)) {
        tmp = elements.n();
      } else {
        tmp = false;
      }
      if (tmp) {
        tmp$ret$0 = true;
        break $l$block_0;
      }
      var tmp0_iterator = elements.k();
      while (tmp0_iterator.r()) {
        var element = tmp0_iterator.s();
        // Inline function 'kotlin.collections.AbstractCollection.containsAll.<anonymous>' call
        if (!this.l(element)) {
          tmp$ret$0 = false;
          break $l$block_0;
        }
      }
      tmp$ret$0 = true;
    }
    return tmp$ret$0;
  };
  protoOf(AbstractCollection).n = function () {
    return this.j() === 0;
  };
  protoOf(AbstractCollection).toString = function () {
    return joinToString_0(this, ', ', '[', ']', VOID, VOID, AbstractCollection$toString$lambda(this));
  };
  protoOf(AbstractCollection).toArray = function () {
    return collectionToArray(this);
  };
  function Companion_4() {
    this.x_1 = 2147483639;
  }
  protoOf(Companion_4).g1 = function (index, size) {
    if (index < 0 ? true : index >= size) {
      throw IndexOutOfBoundsException_init_$Create$_0('index: ' + index + ', size: ' + size);
    }
  };
  protoOf(Companion_4).h1 = function (index, size) {
    if (index < 0 ? true : index > size) {
      throw IndexOutOfBoundsException_init_$Create$_0('index: ' + index + ', size: ' + size);
    }
  };
  protoOf(Companion_4).o4 = function (fromIndex, toIndex, size) {
    if (fromIndex < 0 ? true : toIndex > size) {
      throw IndexOutOfBoundsException_init_$Create$_0('fromIndex: ' + fromIndex + ', toIndex: ' + toIndex + ', size: ' + size);
    }
    if (fromIndex > toIndex) {
      throw IllegalArgumentException_init_$Create$_0('fromIndex: ' + fromIndex + ' > toIndex: ' + toIndex);
    }
  };
  protoOf(Companion_4).x1 = function (oldCapacity, minCapacity) {
    var newCapacity = oldCapacity + (oldCapacity >> 1) | 0;
    if ((newCapacity - minCapacity | 0) < 0)
      newCapacity = minCapacity;
    if ((newCapacity - 2147483639 | 0) > 0)
      newCapacity = minCapacity > 2147483639 ? IntCompanionObject_instance.MAX_VALUE : 2147483639;
    return newCapacity;
  };
  protoOf(Companion_4).z = function (c) {
    var hashCode_0 = 1;
    var tmp0_iterator = c.k();
    while (tmp0_iterator.r()) {
      var e = tmp0_iterator.s();
      var tmp = imul(31, hashCode_0);
      var tmp2_elvis_lhs = e == null ? null : hashCode(e);
      hashCode_0 = tmp + (tmp2_elvis_lhs == null ? 0 : tmp2_elvis_lhs) | 0;
    }
    return hashCode_0;
  };
  protoOf(Companion_4).y = function (c, other) {
    if (!(c.j() === other.j()))
      return false;
    var otherIterator = other.k();
    var tmp0_iterator = c.k();
    while (tmp0_iterator.r()) {
      var elem = tmp0_iterator.s();
      var elemOther = otherIterator.s();
      if (!equals(elem, elemOther)) {
        return false;
      }
    }
    return true;
  };
  var Companion_instance_4;
  function Companion_getInstance_4() {
    return Companion_instance_4;
  }
  function Companion_5() {
  }
  protoOf(Companion_5).b1 = function (c) {
    var hashCode_0 = 0;
    var tmp0_iterator = c.k();
    while (tmp0_iterator.r()) {
      var element = tmp0_iterator.s();
      var tmp = hashCode_0;
      var tmp2_elvis_lhs = element == null ? null : hashCode(element);
      hashCode_0 = tmp + (tmp2_elvis_lhs == null ? 0 : tmp2_elvis_lhs) | 0;
    }
    return hashCode_0;
  };
  protoOf(Companion_5).a1 = function (c, other) {
    if (!(c.j() === other.j()))
      return false;
    // Inline function 'kotlin.collections.containsAll' call
    return c.m(other);
  };
  var Companion_instance_5;
  function Companion_getInstance_5() {
    return Companion_instance_5;
  }
  function collectionToArrayCommonImpl(collection) {
    if (collection.n()) {
      // Inline function 'kotlin.emptyArray' call
      return [];
    }
    // Inline function 'kotlin.arrayOfNulls' call
    var size = collection.j();
    var destination = fillArrayVal(Array(size), null);
    var iterator = collection.k();
    var index = 0;
    while (iterator.r()) {
      var tmp0 = index;
      index = tmp0 + 1 | 0;
      destination[tmp0] = iterator.s();
    }
    return destination;
  }
  function listOf(elements) {
    return elements.length > 0 ? asList(elements) : emptyList();
  }
  function emptyList() {
    return EmptyList_getInstance();
  }
  function EmptyList() {
    EmptyList_instance = this;
    this.r4_1 = new Long(-1478467534, -1720727600);
  }
  protoOf(EmptyList).equals = function (other) {
    var tmp;
    if (!(other == null) ? isInterface(other, List) : false) {
      tmp = other.n();
    } else {
      tmp = false;
    }
    return tmp;
  };
  protoOf(EmptyList).hashCode = function () {
    return 1;
  };
  protoOf(EmptyList).toString = function () {
    return '[]';
  };
  protoOf(EmptyList).j = function () {
    return 0;
  };
  protoOf(EmptyList).n = function () {
    return true;
  };
  protoOf(EmptyList).s4 = function (elements) {
    return elements.n();
  };
  protoOf(EmptyList).m = function (elements) {
    return this.s4(elements);
  };
  protoOf(EmptyList).t = function (index) {
    throw IndexOutOfBoundsException_init_$Create$_0("Empty list doesn't contain element at index " + index + '.');
  };
  protoOf(EmptyList).k = function () {
    return EmptyIterator_instance;
  };
  var EmptyList_instance;
  function EmptyList_getInstance() {
    if (EmptyList_instance == null)
      new EmptyList();
    return EmptyList_instance;
  }
  function EmptyIterator() {
  }
  protoOf(EmptyIterator).r = function () {
    return false;
  };
  protoOf(EmptyIterator).s = function () {
    throw NoSuchElementException_init_$Create$();
  };
  var EmptyIterator_instance;
  function EmptyIterator_getInstance() {
    return EmptyIterator_instance;
  }
  function emptySet() {
    return EmptySet_getInstance();
  }
  function optimizeReadOnlySet(_this__u8e3s4) {
    switch (_this__u8e3s4.j()) {
      case 0:
        return emptySet();
      case 1:
        return setOf(_this__u8e3s4.k().s());
      default:
        return _this__u8e3s4;
    }
  }
  function hashSetOf(elements) {
    return toCollection(elements, HashSet_init_$Create$_0(mapCapacity(elements.length)));
  }
  function EmptySet() {
    EmptySet_instance = this;
    this.t4_1 = new Long(1993859828, 793161749);
  }
  protoOf(EmptySet).equals = function (other) {
    var tmp;
    if (!(other == null) ? isInterface(other, Set) : false) {
      tmp = other.n();
    } else {
      tmp = false;
    }
    return tmp;
  };
  protoOf(EmptySet).hashCode = function () {
    return 0;
  };
  protoOf(EmptySet).toString = function () {
    return '[]';
  };
  protoOf(EmptySet).j = function () {
    return 0;
  };
  protoOf(EmptySet).n = function () {
    return true;
  };
  protoOf(EmptySet).s4 = function (elements) {
    return elements.n();
  };
  protoOf(EmptySet).m = function (elements) {
    return this.s4(elements);
  };
  protoOf(EmptySet).k = function () {
    return EmptyIterator_instance;
  };
  var EmptySet_instance;
  function EmptySet_getInstance() {
    if (EmptySet_instance == null)
      new EmptySet();
    return EmptySet_instance;
  }
  function appendElement(_this__u8e3s4, element, transform) {
    if (!(transform == null)) {
      _this__u8e3s4.f3(transform(element));
    } else {
      if (element == null ? true : isCharSequence(element)) {
        _this__u8e3s4.f3(element);
      } else {
        if (element instanceof Char) {
          _this__u8e3s4.x2(element.u4_1);
        } else {
          _this__u8e3s4.f3(toString_1(element));
        }
      }
    }
  }
  function get_lastIndex(_this__u8e3s4) {
    return charSequenceLength(_this__u8e3s4) - 1 | 0;
  }
  function padStart(_this__u8e3s4, length, padChar) {
    padChar = padChar === VOID ? _Char___init__impl__6a9atx(32) : padChar;
    return toString_2(padStart_0(isCharSequence(_this__u8e3s4) ? _this__u8e3s4 : THROW_CCE(), length, padChar));
  }
  function padStart_0(_this__u8e3s4, length, padChar) {
    padChar = padChar === VOID ? _Char___init__impl__6a9atx(32) : padChar;
    if (length < 0)
      throw IllegalArgumentException_init_$Create$_0('Desired length ' + length + ' is less than zero.');
    if (length <= charSequenceLength(_this__u8e3s4))
      return charSequenceSubSequence(_this__u8e3s4, 0, charSequenceLength(_this__u8e3s4));
    var sb = StringBuilder_init_$Create$(length);
    var inductionVariable = 1;
    var last = length - charSequenceLength(_this__u8e3s4) | 0;
    if (inductionVariable <= last)
      do {
        var i = inductionVariable;
        inductionVariable = inductionVariable + 1 | 0;
        sb.x2(padChar);
      }
       while (!(i === last));
    sb.f3(_this__u8e3s4);
    return sb;
  }
  function _UShort___init__impl__jigrne(data) {
    return data;
  }
  function _UShort___get_data__impl__g0245($this) {
    return $this;
  }
  var page;
  var catalogSearch;
  var catalogCategory;
  var selectedItem;
  var itemPrice;
  var warrantyChecked;
  var priorityShipping;
  var quantity;
  var email;
  var address;
  var promoCode;
  var promoDiscount;
  var accepted;
  var confirmed;
  var orders;
  var profileName;
  var profileEmail;
  var darkTheme;
  var patchAlerts;
  var telemetry;
  var profileStatus;
  var trackingStatus;
  var supportFeedback;
  var ticketStatus;
  function renderScreen(screenId) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(screenId === 'checkout')) {
      // Inline function 'renderScreen.<anonymous>' call
      var message = 'Unknown screen';
      throw IllegalArgumentException_init_$Create$_0(toString_2(message));
    }
    var tmp;
    switch (page) {
      case 'dashboard':
        tmp = listOf([layout('dash_hero', 'card', listOf([text('dash_title', 'PatchRelay Android Hub', 'title'), text('dash_sub', 'High-performance interpreted UI engine with dynamic hot-patching.', 'caption'), text('dash_device', 'Target Host: Realme RMX2117 (Android 11/12)', 'caption')])), navBar(), layout('dash_stats', 'card', listOf([text('stats_title', 'Live Application Metrics', 'title'), text('stat_orders', 'Total Orders Placed: ' + orders), text('stat_items', 'Cart Items: ' + quantity + ' (' + selectedItem + ')'), text('stat_theme', 'Dark Mode: ' + (darkTheme ? 'Enabled' : 'Standard')), text('stat_api', 'API Bridge: ' + get_apiStatus(), 'caption')])), layout('dash_actions', 'row', listOf([button('go_shop', 'Shop Catalog', 'to_catalog'), button('go_api', 'Query Live API', 'to_api')]))]);
        break;
      case 'catalog':
        tmp = listOf([text('cat_title', 'Product Catalog (3 Items)', 'title'), text('cat_desc', 'Browse hardware modules with on-device telemetry.', 'caption'), layout('cat_filter_row', 'row', listOf([button('cat_all', 'All Items', 'cat_filter_all', !(catalogCategory === 'all')), button('cat_ctrl', 'Controllers', 'cat_filter_controllers', !(catalogCategory === 'controllers')), button('cat_sensors', 'Sensors', 'cat_filter_sensors', !(catalogCategory === 'sensors'))])), (catalogCategory === 'all' ? true : catalogCategory === 'controllers') ? layout('item1', 'card', listOf([text('item1_name', 'Orbital AI Controller Node', 'title'), text('item1_price', 'Price: $199 USD', 'caption'), text('item1_desc', 'ARM64 micro-controller for low-latency automation.'), layout('item1_btns', 'row', listOf([button('item1_sel', 'Inspect Specs', 'select_item1'), button('item1_add', 'Quick Add', 'add_item1')]))])) : layout('empty1', 'row', emptyList()), (catalogCategory === 'all' ? true : catalogCategory === 'controllers') ? layout('item2', 'card', listOf([text('item2_name', 'Shizuku Wireless Bridge', 'title'), text('item2_price', 'Price: $79 USD', 'caption'), text('item2_desc', 'Ultra-fast privileged IPC gateway adapter.'), layout('item2_btns', 'row', listOf([button('item2_sel', 'Inspect Specs', 'select_item2'), button('item2_add', 'Quick Add', 'add_item2')]))])) : layout('empty2', 'row', emptyList()), (catalogCategory === 'all' ? true : catalogCategory === 'sensors') ? layout('item3', 'card', listOf([text('item3_name', 'Telemetry Sensor Module', 'title'), text('item3_price', 'Price: $119 USD', 'caption'), text('item3_desc', 'Multi-axis motion & hardware voltage tracker.'), layout('item3_btns', 'row', listOf([button('item3_sel', 'Inspect Specs', 'select_item3'), button('item3_add', 'Quick Add', 'add_item3')]))])) : layout('empty3', 'row', emptyList()), layout('cat_footer', 'row', listOf([button('cat_back', 'Back to Home', 'to_dashboard'), button('cat_checkout', 'Go to Checkout (' + quantity + ')', 'to_checkout')]))]);
        break;
      case 'detail':
        tmp = listOf([text('det_title', 'Product Detail: ' + selectedItem, 'title'), layout('det_specs_card', 'card', listOf([text('det_heading', 'Hardware Specifications', 'caption'), text('det_p1', 'Item: ' + selectedItem), text('det_p2', 'Base Unit Price: $' + itemPrice + ' USD'), text('det_p3', 'Availability: In Stock (Dispatched within 24h)')])), layout('det_addons_card', 'card', listOf([text('addons_title', 'Add-On Upgrades', 'caption'), toggle('toggle_warranty', 'Add 2-Year Extended Care (+$29)', warrantyChecked, 'toggle_warranty'), toggle('toggle_priority', 'Add Priority Courier Delivery (+$15)', priorityShipping, 'toggle_priority')])), layout('det_qty_card', 'card', listOf([text('qty_label', 'Selected Quantity: ' + quantity), layout('qty_buttons', 'row', listOf([button('qty_minus', 'Remove (-1)', 'remove', quantity > 1), button('qty_plus', 'Add (+1)', 'add', quantity < 100)]))])), layout('det_actions', 'row', listOf([button('det_back', 'Back to Catalog', 'to_catalog'), button('det_checkout', 'Checkout ($' + calculateTotal() + ')', 'to_checkout')]))]);
        break;
      case 'checkout':
        var tmp_0;
        if (!confirmed) {
          var tmp_1 = text('chk_title', 'Cart & Multi-Step Checkout', 'title');
          var tmp_2 = layout('chk_summary', 'card', listOf([text('sum_title', 'Order Breakdown', 'caption'), text('sum_item', 'Product: ' + selectedItem + ' (x' + quantity + ')'), text('sum_sub', 'Subtotal: $' + imul(quantity, itemPrice)), warrantyChecked ? text('sum_war', 'Extended Warranty: +$29') : text('sum_war_no', 'Warranty: None', 'caption'), priorityShipping ? text('sum_ship', 'Priority Shipping: +$15') : text('sum_ship_no', 'Standard Shipping: Free', 'caption'), promoDiscount > 0 ? text('sum_disc', 'Discount Applied: -' + promoDiscount) : text('sum_disc_no', 'Promo: None', 'caption'), text('sum_total', 'Grand Total: $' + calculateTotal(), 'title')]));
          var tmp_3 = text('form_title', 'Contact & Delivery', 'caption');
          var tmp_4 = input('chk_email', 'Enter receipt email', email, 'email');
          var tmp_5;
          // Inline function 'kotlin.text.isEmpty' call
          var this_0 = email;
          if (charSequenceLength(this_0) === 0) {
            tmp_5 = 'Receipt email is required.';
          } else {
            if (validEmail()) {
              tmp_5 = 'Email valid.';
            } else {
              tmp_5 = 'Invalid email address.';
            }
          }
          var tmp_6 = tmp_5;
          var tmp_7;
          var tmp_8;
          // Inline function 'kotlin.text.isNotEmpty' call
          var this_1 = email;
          if (charSequenceLength(this_1) > 0) {
            tmp_8 = !validEmail();
          } else {
            tmp_8 = false;
          }
          if (tmp_8) {
            tmp_7 = 'error';
          } else {
            tmp_7 = 'caption';
          }
          var tmp_9 = layout('chk_form', 'card', listOf([tmp_3, tmp_4, text('chk_email_val', tmp_6, tmp_7), input('chk_address', 'Shipping address', address, 'address'), toggle('chk_terms', 'I accept the checkout & warranty terms', accepted, 'terms')]));
          var tmp_10 = 'Place Order ($' + calculateTotal() + ')';
          var tmp_11;
          if (validEmail() ? accepted : false) {
            // Inline function 'kotlin.text.isNotEmpty' call
            var this_2 = address;
            tmp_11 = charSequenceLength(this_2) > 0;
          } else {
            tmp_11 = false;
          }
          tmp_0 = listOf([tmp_1, tmp_2, tmp_9, layout('chk_actions', 'row', listOf([button('chk_pay', tmp_10, 'place_order', tmp_11), button('chk_back', 'Back to Home', 'to_dashboard')]))]);
        } else {
          tmp_0 = listOf([layout('rec_card', 'card', listOf([text('rec_title', 'Order Confirmed!', 'title'), text('rec_id', 'Order Reference: #PR-88' + imul(orders, 17)), text('rec_items', 'Purchased: ' + quantity + ' x ' + selectedItem), text('rec_amount', 'Total Paid: $' + calculateTotal()), text('rec_dest', 'Receipt dispatched to: ' + email), text('rec_status', 'Status: Processing in fulfillment queue.', 'caption')])), layout('rec_btns', 'row', listOf([button('rec_new', 'Start New Order', 'new_order'), button('rec_orders', 'View Order Tracking', 'to_orders')]))]);
        }

        tmp = tmp_0;
        break;
      case 'api_explorer':
        tmp = listOf([text('api_page_title', 'Live Host API Bridge', 'title'), text('api_desc', 'Direct bounded IPC request to native Android host HTTPS client.', 'caption'), layout('api_card', 'card', listOf([text('api_target', 'Target: JetBrains/kotlin on GitHub API', 'caption'), text('api_live_status', get_apiStatus()), button('api_fetch_btn', 'Execute API Call', 'load_repository', !get_apiBusy())])), layout('api_info_card', 'card', listOf([text('info_heading', 'Bridge Architecture Info', 'caption'), text('info_p1', "Calls MainActivity.registerHostFunction('github.repository.v1')"), text('info_p2', 'Native Android downloads payload over TLS using HttpsDownloads engine.'), text('info_p3', 'Result returns asynchronously to screen JS runtime.')])), button('api_back', 'Back to Dashboard', 'to_dashboard')]);
        break;
      case 'profile':
        tmp = listOf([text('prof_title', 'User Profile & Preferences', 'title'), layout('prof_inputs_card', 'card', listOf([text('prof_head', 'Personal Information', 'caption'), input('prof_name_input', 'Full Name', profileName, 'profile_name'), input('prof_email_input', 'Account Email', profileEmail, 'profile_email')])), layout('prof_settings_card', 'card', listOf([text('settings_head', 'Preferences & Features', 'caption'), toggle('prof_toggle_dark', 'Dark Theme Mode', darkTheme, 'toggle_dark'), toggle('prof_toggle_alerts', 'Instant OTA Patch Alerts', patchAlerts, 'toggle_alerts'), toggle('prof_toggle_telem', 'Telemetry Diagnostics', telemetry, 'toggle_telemetry')])), text('prof_feedback', profileStatus, 'caption'), layout('prof_btns', 'row', listOf([button('prof_save', 'Save Preferences', 'save_profile'), button('prof_back', 'Back to Dashboard', 'to_dashboard')]))]);
        break;
      case 'orders':
        tmp = listOf([text('ord_title', 'Order History & Live Tracking', 'title'), layout('ord_active_card', 'card', listOf([text('ord_active_h', 'Latest Active Delivery', 'caption'), text('ord_active_desc', trackingStatus), text('ord_active_step', 'Dispatched -> Bengaluru Customs -> Out for Delivery', 'caption'), button('ord_refresh', 'Check Real-Time Tracking', 'refresh_tracking')])), layout('ord_past_card', 'card', listOf([text('ord_past_h', 'Completed Transactions', 'caption'), text('ord_item1', 'Order #PR-8817: 1x Orbital Controller - $199 [Delivered]'), text('ord_item2', 'Order #PR-8704: 2x Shizuku Bridge - $158 [Delivered]')])), layout('ord_nav_row', 'row', listOf([button('ord_home', 'Back to Dashboard', 'to_dashboard'), button('ord_shop', 'Shop More Items', 'to_catalog')]))]);
        break;
      case 'support':
        var tmp_12 = text('sup_title', 'Customer Help & FAQ Support', 'title');
        var tmp_13 = layout('faq_card1', 'card', listOf([text('faq_q1', 'Q: How do patches update my phone without reinstalling?', 'caption'), text('faq_a1', 'A: PatchRelay compiles Kotlin screens into signed JavaScript executed inside a sandboxed native WebView interpreter.')]));
        var tmp_14 = layout('faq_card2', 'card', listOf([text('faq_q2', 'Q: Can patches break native Android code or permissions?', 'caption'), text('faq_a2', 'A: No. Native files are cryptographically locked in baseline.json. The native contract cannot be altered by patches.')]));
        var tmp_15 = text('sup_form_head', 'Submit Support Inquiry', 'caption');
        var tmp_16 = input('sup_input', 'Describe your question or issue', supportFeedback, 'support_input');
        var tmp_17 = text('sup_status', ticketStatus, 'caption');
        // Inline function 'kotlin.text.isNotEmpty' call

        var this_3 = supportFeedback;
        var tmp$ret$4 = charSequenceLength(this_3) > 0;
        tmp = listOf([tmp_12, tmp_13, tmp_14, layout('sup_form_card', 'card', listOf([tmp_15, tmp_16, tmp_17, button('sup_submit', 'Submit Support Ticket', 'submit_ticket', tmp$ret$4)])), button('sup_back', 'Back to Dashboard', 'to_dashboard')]);
        break;
      default:
        var message_0 = 'Unknown page: ' + page;
        throw IllegalStateException_init_$Create$_0(toString_2(message_0));
    }
    var content = tmp;
    return column(screenId, content, page === 'dashboard' ? null : 'to_dashboard');
  }
  function handleEvent(screenId, action, value) {
    value = value === VOID ? null : value;
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(screenId === 'checkout')) {
      // Inline function 'handleEvent.<anonymous>' call
      var message = 'Unknown screen';
      throw IllegalArgumentException_init_$Create$_0(toString_2(message));
    }
    switch (action) {
      case 'to_dashboard':
        page = 'dashboard';
        break;
      case 'to_catalog':
        page = 'catalog';
        break;
      case 'to_detail':
        page = 'detail';
        break;
      case 'to_checkout':
        page = 'checkout';
        break;
      case 'to_api':
        page = 'api_explorer';
        break;
      case 'to_profile':
        page = 'profile';
        break;
      case 'to_orders':
        page = 'orders';
        break;
      case 'to_support':
        page = 'support';
        break;
      case 'cat_filter_all':
        catalogCategory = 'all';
        break;
      case 'cat_filter_controllers':
        catalogCategory = 'controllers';
        break;
      case 'cat_filter_sensors':
        catalogCategory = 'sensors';
        break;
      case 'select_item1':
        selectedItem = 'Orbital AI Controller Node';
        itemPrice = 199;
        page = 'detail';
        break;
      case 'select_item2':
        selectedItem = 'Shizuku Wireless Bridge';
        itemPrice = 79;
        page = 'detail';
        break;
      case 'select_item3':
        selectedItem = 'Telemetry Sensor Module';
        itemPrice = 119;
        page = 'detail';
        break;
      case 'add_item1':
        selectedItem = 'Orbital AI Controller Node';
        itemPrice = 199;
        quantity = 1;
        page = 'checkout';
        break;
      case 'add_item2':
        selectedItem = 'Shizuku Wireless Bridge';
        itemPrice = 79;
        quantity = 1;
        page = 'checkout';
        break;
      case 'add_item3':
        selectedItem = 'Telemetry Sensor Module';
        itemPrice = 119;
        quantity = 1;
        page = 'checkout';
        break;
      case 'toggle_warranty':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(value === 'true' ? true : value === 'false')) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_0 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_0));
        }

        warrantyChecked = value === 'true';
        break;
      case 'toggle_priority':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(value === 'true' ? true : value === 'false')) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_1 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_1));
        }

        priorityShipping = value === 'true';
        break;
      case 'add':
        if (quantity < 100) {
          quantity = quantity + 1 | 0;
        }

        break;
      case 'remove':
        if (quantity > 1) {
          quantity = quantity - 1 | 0;
        }

        break;
      case 'email':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(!(value == null) ? value.length <= 1024 : false)) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_2 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_2));
        }

        email = value;
        break;
      case 'address':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(!(value == null) ? value.length <= 1024 : false)) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_3 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_3));
        }

        address = value;
        break;
      case 'terms':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(value === 'true' ? true : value === 'false')) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_4 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_4));
        }

        accepted = value === 'true';
        break;
      case 'place_order':
        var tmp;
        if (validEmail() ? accepted : false) {
          // Inline function 'kotlin.text.isNotEmpty' call
          var this_0 = address;
          tmp = charSequenceLength(this_0) > 0;
        } else {
          tmp = false;
        }

        if (tmp) {
          confirmed = true;
          orders = orders + 1 | 0;
        }

        break;
      case 'new_order':
        confirmed = false;
        quantity = 1;
        warrantyChecked = false;
        priorityShipping = false;
        promoDiscount = 0;
        page = 'catalog';
        break;
      case 'load_repository':
        requestRepository('JetBrains/kotlin');
        break;
      case 'profile_name':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(!(value == null) ? value.length <= 1024 : false)) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_5 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_5));
        }

        profileName = value;
        break;
      case 'profile_email':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(!(value == null) ? value.length <= 1024 : false)) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_6 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_6));
        }

        profileEmail = value;
        break;
      case 'toggle_dark':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(value === 'true' ? true : value === 'false')) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_7 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_7));
        }

        darkTheme = value === 'true';
        break;
      case 'toggle_alerts':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(value === 'true' ? true : value === 'false')) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_8 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_8));
        }

        patchAlerts = value === 'true';
        break;
      case 'toggle_telemetry':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(value === 'true' ? true : value === 'false')) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_9 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_9));
        }

        telemetry = value === 'true';
        break;
      case 'save_profile':
        profileStatus = 'Saved: ' + profileName + ' (' + profileEmail + ')';
        break;
      case 'refresh_tracking':
        trackingStatus = 'Tracking updated at ' + (1000 + imul(orders, 37) | 0) + ' ms: In Transit';
        break;
      case 'support_input':
        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        // Inline function 'kotlin.require' call

        // Inline function 'kotlin.contracts.contract' call

        if (!(!(value == null) ? value.length <= 1024 : false)) {
          // Inline function 'kotlin.require.<anonymous>' call
          var message_10 = 'Failed requirement.';
          throw IllegalArgumentException_init_$Create$_0(toString_2(message_10));
        }

        supportFeedback = value;
        break;
      case 'submit_ticket':
        ticketStatus = 'Ticket #PR-' + (2000 + orders | 0) + ' submitted successfully!';
        supportFeedback = '';
        break;
      default:
        // Inline function 'kotlin.error' call

        var message_11 = 'Unsupported action: ' + action;
        throw IllegalStateException_init_$Create$_0(toString_2(message_11));
    }
    return renderScreen(screenId);
  }
  function validEmail() {
    var tmp;
    if (email.length <= 254) {
      // Inline function 'kotlin.text.matches' call
      var this_0 = email;
      tmp = Regex_init_$Create$('[^\\s@]+@[^\\s@]+\\.[^\\s@]+').o3(this_0);
    } else {
      tmp = false;
    }
    return tmp;
  }
  function calculateTotal() {
    var base = imul(quantity, itemPrice);
    var warranty = warrantyChecked ? 29 : 0;
    var shipping = priorityShipping ? 15 : 0;
    var discount = promoDiscount;
    return coerceAtLeast(((base + warranty | 0) + shipping | 0) - discount | 0, 0);
  }
  function navBar() {
    return layout('nav_bar', 'card', listOf([text('nav_title', 'PatchRelay Navigation Hub', 'caption'), layout('nav_row1', 'row', listOf([button('nav_home', 'Dashboard', 'to_dashboard', !(page === 'dashboard')), button('nav_cat', 'Catalog', 'to_catalog', !(page === 'catalog')), button('nav_det', 'Detail', 'to_detail', !(page === 'detail'))])), layout('nav_row2', 'row', listOf([button('nav_chk', 'Checkout', 'to_checkout', !(page === 'checkout')), button('nav_api', 'API Feed', 'to_api', !(page === 'api_explorer')), button('nav_prf', 'Profile', 'to_profile', !(page === 'profile'))])), layout('nav_row3', 'row', listOf([button('nav_ord', 'Orders', 'to_orders', !(page === 'orders')), button('nav_sup', 'Support', 'to_support', !(page === 'support'))]))]));
  }
  function get_apiStatus() {
    return apiStatus;
  }
  var apiStatus;
  function get_apiBusy() {
    return apiBusy;
  }
  var apiBusy;
  var requestId;
  var pendingHostRequest;
  function takeHostRequests() {
    // Inline function 'kotlin.also' call
    var this_0 = pendingHostRequest;
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'takeHostRequests.<anonymous>' call
    pendingHostRequest = '[]';
    return this_0;
  }
  function handleHostResult(screenId, id, result, error) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(screenId === 'checkout')) {
      // Inline function 'kotlin.require.<anonymous>' call
      var message = 'Failed requirement.';
      throw IllegalArgumentException_init_$Create$_0(toString_2(message));
    }
    if (apiBusy ? id === requestId : false) {
      apiBusy = false;
      var tmp;
      if (!(error == null)) {
        tmp = 'API unavailable: ' + error;
      } else {
        tmp = 'API result: ' + (result == null ? 'No result' : result);
      }
      apiStatus = tmp;
    }
    return renderScreen(screenId);
  }
  function requestRepository(target) {
    target = target === VOID ? 'JetBrains/kotlin' : target;
    if (apiBusy)
      return Unit_instance;
    // Inline function 'kotlin.check' call
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlin.check' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(requestId < IntCompanionObject_instance.MAX_VALUE)) {
      // Inline function 'kotlin.check.<anonymous>' call
      var message = 'Check failed.';
      throw IllegalStateException_init_$Create$_0(toString_2(message));
    }
    requestId = requestId + 1 | 0;
    apiBusy = true;
    apiStatus = 'Loading repository through Kotlin...';
    pendingHostRequest = '[{"id":' + requestId + ',"function":"github.repository.v1","argument":"' + target + '"}]';
  }
  function patchedFunctionIds() {
    return '["checkout.total.v1"]';
  }
  function invokePatch(functionId, first, second) {
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!(functionId === 'checkout.total.v1')) {
      // Inline function 'invokePatch.<anonymous>' call
      var message = 'Unknown function';
      throw IllegalArgumentException_init_$Create$_0(toString_2(message));
    }
    // Inline function 'kotlin.require' call
    // Inline function 'kotlin.contracts.contract' call
    if (!((0 <= first ? first <= 100 : false) ? 0 <= second ? second <= 1000000 : false : false)) {
      // Inline function 'invokePatch.<anonymous>' call
      var message_0 = 'Unsupported calculation range';
      throw IllegalArgumentException_init_$Create$_0(toString_2(message_0));
    }
    return imul(first, second).toString();
  }
  function jsonString(value) {
    // Inline function 'kotlin.text.buildString' call
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'kotlin.apply' call
    var this_0 = StringBuilder_init_$Create$_0();
    // Inline function 'kotlin.contracts.contract' call
    // Inline function 'jsonString.<anonymous>' call
    this_0.x2(_Char___init__impl__6a9atx(34));
    // Inline function 'kotlin.text.forEach' call
    var inductionVariable = 0;
    while (inductionVariable < charSequenceLength(value)) {
      var element = charSequenceGet(value, inductionVariable);
      inductionVariable = inductionVariable + 1 | 0;
      // Inline function 'jsonString.<anonymous>.<anonymous>' call
      if (element === _Char___init__impl__6a9atx(34)) {
        this_0.w2('\\"');
      } else if (element === _Char___init__impl__6a9atx(92)) {
        this_0.w2('\\\\');
      } else if (element === _Char___init__impl__6a9atx(10)) {
        this_0.w2('\\n');
      } else if (element === _Char___init__impl__6a9atx(13)) {
        this_0.w2('\\r');
      } else if (element === _Char___init__impl__6a9atx(9)) {
        this_0.w2('\\t');
      } else {
        // Inline function 'kotlin.code' call
        if (Char__toInt_impl_vasixd(element) < 32) {
          // Inline function 'kotlin.code' call
          var tmp$ret$1 = Char__toInt_impl_vasixd(element);
          this_0.w2('\\u' + padStart(toString(tmp$ret$1, 16), 4, _Char___init__impl__6a9atx(48)));
        } else {
          this_0.x2(element);
        }
      }
    }
    this_0.x2(_Char___init__impl__6a9atx(34));
    return this_0.toString();
  }
  function widget(id, type, fields) {
    return '{"id":' + jsonString(id) + ',"type":' + jsonString(type) + ',' + fields + '}';
  }
  function text(id, value, style) {
    style = style === VOID ? 'body' : style;
    return widget(id, 'text', '"text":' + jsonString(value) + ',"style":' + jsonString(style));
  }
  function button(id, label, action, enabled) {
    enabled = enabled === VOID ? true : enabled;
    return widget(id, 'button', '"text":' + jsonString(label) + ',"action":' + jsonString(action) + ',"enabled":' + enabled);
  }
  function input(id, label, value, action) {
    return widget(id, 'input', '"text":' + jsonString(label) + ',"value":' + jsonString(value) + ',"action":' + jsonString(action));
  }
  function toggle(id, label, checked, action) {
    return widget(id, 'toggle', '"text":' + jsonString(label) + ',"checked":' + checked + ',"action":' + jsonString(action));
  }
  function layout(id, type, nodes) {
    return widget(id, type, '"children":[' + joinToString_0(nodes, ',') + ']');
  }
  function column(screenId, nodes, backAction) {
    backAction = backAction === VOID ? null : backAction;
    return '{"screenId":' + jsonString(screenId) + ',"type":"column","children":[' + joinToString_0(nodes, ',') + ']' + (backAction == null ? '' : ',"backAction":' + jsonString(backAction)) + '}';
  }
  //region block: post-declaration
  protoOf(InternalHashMap).z1 = containsAllEntries;
  //endregion
  //region block: init
  Unit_instance = new Unit();
  IntCompanionObject_instance = new IntCompanionObject();
  Companion_instance_0 = new Companion_0();
  Companion_instance_4 = new Companion_4();
  Companion_instance_5 = new Companion_5();
  EmptyIterator_instance = new EmptyIterator();
  page = 'dashboard';
  catalogSearch = '';
  catalogCategory = 'all';
  selectedItem = 'Orbital AI Controller Node';
  itemPrice = 199;
  warrantyChecked = false;
  priorityShipping = false;
  quantity = 1;
  email = '';
  address = '';
  promoCode = '';
  promoDiscount = 0;
  accepted = false;
  confirmed = false;
  orders = 1;
  profileName = 'Developer Jay';
  profileEmail = 'jaypal@developer.com';
  darkTheme = false;
  patchAlerts = true;
  telemetry = false;
  profileStatus = 'Profile is up to date.';
  trackingStatus = 'Order #PR-9802: Out for delivery in Bengaluru';
  supportFeedback = '';
  ticketStatus = 'Submit a message to reach developer support.';
  apiStatus = 'Repository API has not been called.';
  apiBusy = false;
  requestId = 0;
  pendingHostRequest = '[]';
  //endregion
  //region block: exports
  function $jsExportAll$(_) {
    _.renderScreen = renderScreen;
    _.handleEvent = handleEvent;
    _.takeHostRequests = takeHostRequests;
    _.handleHostResult = handleHostResult;
    _.patchedFunctionIds = patchedFunctionIds;
    _.invokePatch = invokePatch;
  }
  $jsExportAll$(_);
  //endregion
  return _;
}));

//# sourceMappingURL=PatchRelayScreens.js.map
