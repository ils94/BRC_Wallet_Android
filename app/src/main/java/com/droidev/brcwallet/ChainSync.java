package com.droidev.brcwallet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

public final class ChainSync {

    private static final int TAG_LOCK = 0x4c4f434b;
    private static final int TAG_REDEEM = 0x52444d31;
    private static final long HALVING_INTERVAL = 210_000L;
    private static final long INITIAL_REWARD = 50L * TxBuilder.COIN;
    private static final int PAGE = 200;

    private static final int MAX_RETRIES = 5;
    private static final long BASE_RETRY_DELAY_MS = 2_000;
    private static final long REQUEST_INTERVAL_MS = 250;

    public interface Progress {
        void onProgress(long height, long tip);
    }

    public static final class AccountState {
        public long height = -1;
        public long balanceWei = 0;
        public long nonce = 0;
    }

    public static void sync(BRCApi api, byte[] ourAddr, AccountState state,
                            Progress progress, List<TxRecord> history) throws IOException {
        BRCApi.Tip tip = api.getTip();
        long from = state.height + 1;

        while (from <= tip.height) {
            List<byte[]> blocks = fetchBlocksWithRetry(api, from);

            if (blocks.isEmpty()) break;

            for (byte[] block : blocks) {
                applyBlock(block, ourAddr, state, history);
            }

            from = state.height + 1;

            if (progress != null) {
                progress.onProgress(state.height, tip.height);
            }

            sleep(REQUEST_INTERVAL_MS);
        }
    }

    private static List<byte[]> fetchBlocksWithRetry(BRCApi api, long from)
            throws IOException {
        int attempt = 0;

        while (true) {
            try {
                return api.getBlocks(from, ChainSync.PAGE);
            } catch (IOException e) {
                attempt++;
                if (attempt > MAX_RETRIES) {
                    throw e;
                }

                long delay = BASE_RETRY_DELAY_MS * (1L << (attempt - 1));
                sleep(delay);
            }
        }
    }

    private static void sleep(long ms) throws IOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    static void applyBlock(byte[] block, byte[] ourAddr, AccountState s, List<TxRecord> history) {
        if (block.length < 152) throw new IllegalArgumentException("truncated block");
        ByteBuffer h = ByteBuffer.wrap(block).order(ByteOrder.BIG_ENDIAN);

        long height = h.getInt(0) & 0xFFFFFFFFL;
        byte[] miner = new byte[32];
        h.position(116);
        h.get(miner);

        long blockFees = 0;
        int off = 148;
        long txCount = u32(block, off);
        off += 4;

        for (long i = 0; i < txCount; i++) {
            int tag = (int) u32(block, off);
            if (tag == TxBuilder.CHAIN_ID) {
                byte[] from = Arrays.copyOfRange(block, off + 4, off + 36);
                byte[] to = Arrays.copyOfRange(block, off + 36, off + 68);
                long amount = i64(block, off + 68);
                long fee = i64(block, off + 76);
                long nonce = u32(block, off + 84);
                blockFees += fee;

                byte[] txBytes = Arrays.copyOfRange(block, off, off + 152);
                String txid = TxBuilder.sha256Hex(txBytes);

                boolean isFromUs = Arrays.equals(from, ourAddr);
                boolean isToUs = Arrays.equals(to, ourAddr);

                if (isFromUs) {
                    s.balanceWei -= (amount + fee);
                    s.nonce = nonce + 1;
                    if (history != null) {
                        history.add(new TxRecord(TxRecord.Type.SEND, height, txid,
                                TxBuilder.toHex(from), TxBuilder.toHex(to), amount, fee, nonce));
                    }
                }
                if (isToUs && !isFromUs) {
                    s.balanceWei += amount;
                    if (history != null) {
                        history.add(new TxRecord(TxRecord.Type.RECEIVE, height, txid,
                                TxBuilder.toHex(from), TxBuilder.toHex(to), amount, fee, nonce));
                    }
                }
                off += 152;
            } else if (tag == TAG_LOCK) {
                byte[] from = Arrays.copyOfRange(block, off + 8, off + 40);
                long amount = i64(block, off + 40);
                long fee = i64(block, off + 48);
                long nonce = u32(block, off + 56);
                blockFees += fee;

                byte[] txBytes = Arrays.copyOfRange(block, off, off + 156);
                String txid = TxBuilder.sha256Hex(txBytes);

                if (Arrays.equals(from, ourAddr)) {
                    s.balanceWei -= (amount + fee);
                    s.nonce = nonce + 1;
                    if (history != null) {
                        history.add(new TxRecord(TxRecord.Type.LOCK, height, txid,
                                TxBuilder.toHex(from), "", amount, fee, nonce));
                    }
                }
                off += 156;
            } else if (tag == TAG_REDEEM) {
                byte[] to = Arrays.copyOfRange(block, off + 36, off + 68);
                long amount = i64(block, off + 68);
                long fee = i64(block, off + 76);
                blockFees += fee;

                int scriptLen = u16(block, off + 84);
                int p = off + 86 + scriptLen;
                int witnessCount = block[p] & 0xFF;
                p++;
                for (int w = 0; w < witnessCount; w++) {
                    int len = u16(block, p);
                    p += 2 + len;
                }

                byte[] txBytes = Arrays.copyOfRange(block, off, p);
                String txid = TxBuilder.sha256Hex(txBytes);

                if (Arrays.equals(to, ourAddr)) {
                    s.balanceWei += (amount - fee);
                    if (history != null) {
                        history.add(new TxRecord(TxRecord.Type.REDEEM, height, txid,
                                "", TxBuilder.toHex(to), amount, fee, 0));
                    }
                }
                off = p;
            } else {
                throw new IllegalArgumentException(
                        "Unknown tx tag 0x" + Integer.toHexString(tag) + " in block " + height);
            }
        }

        if (height > 0) {
            long halvings = height / HALVING_INTERVAL;
            long subsidy = halvings >= 64 ? 0 : INITIAL_REWARD >> halvings;
            if (Arrays.equals(miner, ourAddr)) {
                s.balanceWei += subsidy + blockFees;
                if (history != null) {
                    history.add(new TxRecord(TxRecord.Type.MINE, height, "",
                            "", TxBuilder.toHex(miner), subsidy + blockFees, 0, 0));
                }
            }
        }

        s.height = height;
    }

    private static long u32(byte[] b, int off) {
        return ((long) ByteBuffer.wrap(b, off, 4).order(ByteOrder.BIG_ENDIAN).getInt()) & 0xFFFFFFFFL;
    }

    private static int u16(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 2).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
    }

    private static long i64(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 8).order(ByteOrder.BIG_ENDIAN).getLong();
    }
}