package cn.eveonecat.pocket;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoomResultMatchTest {
    @Test public void acceptsExplicitLabelAcrossVersions(){assertTrue(RoomResultMatch.matches("房间号：1754708052","1754708052"));assertTrue(RoomResultMatch.matches("房间号: 1754708052","1754708052"));}
    @Test public void searchInputAloneIsNotResultEvidence(){assertFalse(RoomResultMatch.matches("1754708052","1754708052"));assertFalse(RoomResultMatch.matches("搜索1754708052","1754708052"));}
    @Test public void rejectsLongerRoomWithMatchingPrefix(){assertFalse(RoomResultMatch.matches("房间号：17547080520","1754708052"));assertFalse(RoomResultMatch.matches("房间号：1754708052","17547080520"));}
    @Test public void rejectsAggregateOfMultipleCards(){assertFalse(RoomResultMatch.matches("房间号：1754708052\n房间号：12345","1754708052"));}
}
