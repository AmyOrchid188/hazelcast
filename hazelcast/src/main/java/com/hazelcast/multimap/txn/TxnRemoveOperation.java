/*
 * Copyright (c) 2008-2013, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.multimap.txn;

import com.hazelcast.multimap.*;
import com.hazelcast.multimap.operations.MultiMapKeyBasedOperation;
import com.hazelcast.core.EntryEventType;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.spi.BackupAwareOperation;
import com.hazelcast.spi.Operation;
import com.hazelcast.util.Clock;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author ali 4/5/13
 */
public class TxnRemoveOperation extends MultiMapKeyBasedOperation implements BackupAwareOperation {

    long recordId;
    Data value;
    transient long begin = -1;

    public TxnRemoveOperation() {
    }

    public TxnRemoveOperation(String name, Data dataKey, long recordId, Data value) {
        super(name, dataKey);
        this.recordId = recordId;
        this.value = value;
    }

    public void run() throws Exception {
        begin = Clock.currentTimeMillis();
        MultiMapContainer container = getOrCreateContainer();
        MultiMapWrapper wrapper = container.getMultiMapWrapper(dataKey);
        response = true;
        if (wrapper == null || !wrapper.containsRecordId(recordId)) {
            response = false;
            return;
        }
        Collection<MultiMapRecord> coll = wrapper.getCollection();
        Iterator<MultiMapRecord> iter = coll.iterator();
        while (iter.hasNext()){
            if (iter.next().getRecordId() == recordId){
                iter.remove();
                break;
            }
        }
        if (coll.isEmpty()) {
            remove();
        }
    }

    public void afterRun() throws Exception {
        long elapsed = Math.max(0, Clock.currentTimeMillis()-begin);
        final MultiMapService service = getService();
        service.getLocalMultiMapStatsImpl(name).incrementRemoves(elapsed);
        if (Boolean.TRUE.equals(response)) {
            getOrCreateContainer().update();
            publishEvent(EntryEventType.REMOVED, dataKey, value);
        }
    }

    public boolean shouldBackup() {
        return Boolean.TRUE.equals(response);
    }

    public Operation getBackupOperation() {
        return new TxnRemoveBackupOperation(name, dataKey, recordId, value);
    }

    public long getRecordId() {
        return recordId;
    }

    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeLong(recordId);
        value.writeData(out);
    }

    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        recordId = in.readLong();
        value = new Data();
        value.readData(in);
    }

    public int getId() {
        return MultiMapDataSerializerHook.TXN_REMOVE;
    }

}
