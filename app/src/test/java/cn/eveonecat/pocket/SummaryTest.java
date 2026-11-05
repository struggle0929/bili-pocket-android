package cn.eveonecat.pocket;

import org.junit.Test;
import static org.junit.Assert.*;

public class SummaryTest {
    @Test public void unresolvedParticipationDoesNotDiluteSettledWinRate(){assertEquals("20.0%",new Repo.Summary(100,2,8,6,3,0).rate());}
    @Test public void emptyResultsHaveNoInventedWinRate(){assertEquals("—",new Repo.Summary(3,0,0,0,0,0).rate());}
    @Test public void winningUnknownAmountStillCountsAsWinning(){assertEquals("50.0%",new Repo.Summary(2,1,1,0,0,1).rate());}
}
