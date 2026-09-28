package com.mengsama.mod.mengsamanetmusic.listening;

import com.mengsama.mod.mengsamanetmusic.api.SongInfo;
import java.util.*;

 
final class ListeningPlaybackBook {
    static final class Play {
        final String key,title,detail;
        long generation;
        private boolean counted;
        Play(SongInfo info,long generation){
            key=identity(info);title=ListeningLedger.clean(info.songName,160);
            detail=ListeningLedger.clean(SongInfo.getSourceDisplayName(info.source)+" · "+String.join(" / ",info.artists==null?List.of():info.artists),160);
            this.generation=generation;
        }
        void heard(ListeningLedger ledger){if(!counted){counted=true;ledger.played(key,title,detail);}}
    }
    private final Map<String,Play> plays=new LinkedHashMap<>();
    Play get(String target){return plays.get(target);}
    Play accept(String target,SongInfo song,long generation,boolean continuation){
        Play previous=plays.get(target);
        if(previous!=null && generation<previous.generation)return null;
        Play result=previous;
        if(previous==null || !identity(song).equals(previous.key) || generation!=previous.generation && !continuation)result=new Play(song,generation);
        result.generation=generation;plays.put(target,result);
        if(plays.size()>4096)plays.remove(plays.keySet().iterator().next());
        return result;
    }
    private static String identity(SongInfo info){
         
        return UUID.nameUUIDFromBytes(info.identityKey().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
