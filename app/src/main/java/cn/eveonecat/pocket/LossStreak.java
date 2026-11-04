package cn.eveonecat.pocket;
final class LossStreak {
    static int afterLatestWin(Iterable<String> newestFirst){int n=0;for(String state:newestFirst){if(state.equals("中奖"))break;if(state.equals("未中奖"))n++;}return n;}
}
