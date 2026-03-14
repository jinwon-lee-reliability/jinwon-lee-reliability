package core;

import javax.transaction.xa.Xid;
import java.util.UUID;

public class XaIdGenerator {

    public static Xid create(){

        byte[] gid =
                UUID.randomUUID()
                        .toString()
                        .getBytes();

        return new SimpleXid(
                1,
                gid,
                gid
        );

    }

}