package dev.starsector.preflight.synthetic;

import dev.starsector.preflight.core.Hashes;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;

/** Persistent exact provider index for loose files and JAR entries in explicit mod order. */
final class SyntheticExtendedResourceIndex {
    private static final byte[] MAGIC = {'S','P','X','R'};
    private static final int VERSION = 1, MAX_PROVIDERS = 100_000;
    private static final int MAX_STRING_BYTES = 16 * 1024, MAX_FILE_BYTES = 128 * 1024 * 1024;
    private static final int MAX_RESOURCE_BYTES = 32 * 1024 * 1024;
    private final Path profileRoot;
    private final Map<String,Provider> providers;
    private final int collidedPaths;

    enum Kind { JAR, LOOSE }
    enum Status { HIT, MISS, CORRUPT, ERROR }
    record Provider(Kind kind, String relativeSource, String entryName, String sha256, long bytes) {
        Provider {
            Objects.requireNonNull(kind); relativeSource = relative(relativeSource);
            entryName = entryName == null ? "" : entryName;
            if (kind == Kind.JAR) entryName = logical(entryName); else if (!entryName.isEmpty()) throw new IllegalArgumentException("Loose provider entry");
            hash(sha256); if (bytes < 0 || bytes > MAX_RESOURCE_BYTES) throw new IllegalArgumentException("Resource bytes");
        }
    }
    record Build(SyntheticExtendedResourceIndex index, long jarScans, long looseVisits, long bytesHashed) {}
    record Lookup(Status status, SyntheticExtendedResourceIndex index, String detail) {
        Lookup { Objects.requireNonNull(status); detail = detail == null ? "" : detail;
            if ((status == Status.HIT) != (index != null)) throw new IllegalArgumentException("Lookup invariant"); }
    }

    private SyntheticExtendedResourceIndex(Path root, Map<String,Provider> providers, int collisions) {
        this.profileRoot = root.toAbsolutePath().normalize();
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(providers));
        this.collidedPaths = collisions;
    }
    Map<String,Provider> providers(){return providers;} int collidedPaths(){return collidedPaths;} int providerCount(){return providers.size();}

    static Build build(Path profileRoot) throws IOException {
        Path root = profileRoot.toAbsolutePath().normalize(); Path mods = root.resolve("mods");
        List<String> order = modOrder(root.resolve("mod-order.txt"));
        TreeMap<String,Provider> winners = new TreeMap<>(); Set<String> collisions = new TreeSet<>();
        long jarScans=0, looseVisits=0, bytesHashed=0;
        for (String modName : order) {
            Path mod = mods.resolve(modName).normalize();
            if (!mod.startsWith(mods) || Files.isSymbolicLink(mod) || !Files.isDirectory(mod, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing mod " + modName);
            List<Path> jars=new ArrayList<>(), loose=new ArrayList<>();
            try(var stream=Files.walk(mod)){var it=stream.iterator(); while(it.hasNext()){
                Path p=it.next(); if(Files.isSymbolicLink(p)) throw new IOException("Symbolic profile source: "+p);
                if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))continue;
                if(jars.size()+loose.size()>=MAX_PROVIDERS)throw new IOException("Physical file limit");
                if(p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))jars.add(p); else loose.add(p);
            }}
            jars.sort(Comparator.comparing(p->norm(root.relativize(p)))); loose.sort(Comparator.comparing(p->norm(mod.relativize(p))));
            for(Path jar:jars){jarScans++; try(JarFile archive=new JarFile(jar.toFile())){
                List<String> entries=archive.stream().filter(e->!e.isDirectory()).map(e->logical(e.getName())).sorted().toList();
                for(String name:entries){byte[] b=readJar(archive,archive.getJarEntry(name),name); bytesHashed=Math.addExact(bytesHashed,b.length);
                    put(winners,collisions,name,new Provider(Kind.JAR,norm(root.relativize(jar)),name,Hashes.sha256(b),b.length));}
            }}
            for(Path file:loose){looseVisits++; String name=logical(mod.relativize(file).toString()); long size=Files.size(file);
                if(size>MAX_RESOURCE_BYTES)throw new IOException("Loose resource too large"); bytesHashed=Math.addExact(bytesHashed,size);
                put(winners,collisions,name,new Provider(Kind.LOOSE,norm(root.relativize(file)),"",Hashes.sha256(file),size));}
        }
        return new Build(new SyntheticExtendedResourceIndex(root,winners,collisions.size()),jarScans,looseVisits,bytesHashed);
    }

    byte[] readBytes(String logical) throws IOException {
        Provider p=providers.get(logical); if(p==null)throw new IOException("Missing provider "+logical); Path source=source(p.relativeSource()); byte[] bytes;
        if(p.kind()==Kind.LOOSE){bytes=bounded(source,MAX_RESOURCE_BYTES);}else try(JarFile jar=new JarFile(source.toFile())){bytes=readJar(jar,jar.getJarEntry(p.entryName()),logical);}
        if(bytes.length!=p.bytes()||!Hashes.sha256(bytes).equals(p.sha256()))throw new IOException("Provider identity changed: "+logical); return bytes;
    }
    Path loosePath(String logical) throws IOException {Provider p=providers.get(logical);if(p==null||p.kind()!=Kind.LOOSE)throw new IOException("Expected loose provider");return source(p.relativeSource());}
    String providerDigest() {MessageDigest d=sha(); for(var e:providers.entrySet()){update(d,e.getKey());Provider p=e.getValue();update(d,p.kind().name());update(d,p.relativeSource());update(d,p.entryName());update(d,p.sha256());d.update(ByteBuffer.allocate(8).putLong(p.bytes()).array());}return HexFormat.of().formatHex(d.digest());}

    static Path path(Path cacheRoot,String fingerprint){hash(fingerprint);Path root=cacheRoot.toAbsolutePath().normalize();Path p=root.resolve("synthetic-startup/extended-index").resolve(fingerprint.substring(0,2)).resolve(fingerprint+".spxr").normalize();if(!p.startsWith(root))throw new IllegalArgumentException("Index path escape");return p;}
    void write(Path target,String fingerprint)throws IOException {byte[] payload=encode(fingerprint);ByteArrayOutputStream bytes=new ByteArrayOutputStream(payload.length+44);try(DataOutputStream out=new DataOutputStream(bytes)){out.write(MAGIC);out.writeInt(VERSION);out.writeInt(payload.length);out.write(payload);out.write(Hashes.sha256Bytes(payload));}atomic(target,bytes.toByteArray());}
    static Lookup lookup(Path target,Path profile,String fingerprint){try{hash(fingerprint);}catch(RuntimeException e){return new Lookup(Status.ERROR,null,msg(e));}
        if(!Files.exists(target))return new Lookup(Status.MISS,null,"No extended index");if(Files.isSymbolicLink(target)||!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))return new Lookup(Status.ERROR,null,"Index path is not regular");
        byte[] file;try{long size=Files.size(target);if(size<44||size>MAX_FILE_BYTES)return new Lookup(Status.CORRUPT,null,"Index size");file=Files.readAllBytes(target);}catch(NoSuchFileException e){return new Lookup(Status.MISS,null,"Index disappeared");}catch(IOException e){return new Lookup(Status.ERROR,null,msg(e));}
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(file))){if(!Arrays.equals(in.readNBytes(4),MAGIC))throw new IOException("Index magic");if(in.readInt()!=VERSION)throw new IOException("Index version");int length=in.readInt();if(length<40||length+44L!=file.length)throw new IOException("Index length");byte[] payload=in.readNBytes(length),sum=in.readNBytes(32);if(payload.length!=length||sum.length!=32)throw new EOFException();if(!MessageDigest.isEqual(sum,Hashes.sha256Bytes(payload)))throw new IOException("Index checksum");return new Lookup(Status.HIT,decode(payload,profile,fingerprint),"");}
        catch(IOException|IllegalArgumentException e){return new Lookup(Status.CORRUPT,null,msg(e));}catch(ThreadDeath|VirtualMachineError e){throw e;}catch(Throwable e){return new Lookup(Status.ERROR,null,msg(e));}}

    private byte[] encode(String fingerprint)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){out.write(HexFormat.of().parseHex(fingerprint));out.writeInt(collidedPaths);out.writeInt(providers.size());for(var e:providers.entrySet()){str(out,e.getKey());Provider p=e.getValue();out.writeByte(p.kind().ordinal());str(out,p.relativeSource());str(out,p.entryName());out.write(HexFormat.of().parseHex(p.sha256()));out.writeLong(p.bytes());}}if(bytes.size()>MAX_FILE_BYTES-44)throw new IOException("Index payload limit");return bytes.toByteArray();}
    private static SyntheticExtendedResourceIndex decode(byte[] payload,Path profile,String fingerprint)throws IOException{try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload))){byte[] id=in.readNBytes(32);if(!MessageDigest.isEqual(id,HexFormat.of().parseHex(fingerprint)))throw new IOException("Index identity");int collisions=in.readInt(),count=in.readInt();if(collisions<0||count<0||count>MAX_PROVIDERS||collisions>count)throw new IOException("Index counts");TreeMap<String,Provider> map=new TreeMap<>();String previous=null;for(int i=0;i<count;i++){String name=logical(read(in));if(previous!=null&&previous.compareTo(name)>=0)throw new IOException("Index order");previous=name;int kind=in.readUnsignedByte();if(kind>=Kind.values().length)throw new IOException("Provider kind");String relative=read(in),entry=read(in);byte[] hash=in.readNBytes(32);if(hash.length!=32)throw new EOFException();long size=in.readLong();map.put(name,new Provider(Kind.values()[kind],relative,entry,HexFormat.of().formatHex(hash),size));}if(in.available()!=0)throw new IOException("Trailing index data");SyntheticExtendedResourceIndex index=new SyntheticExtendedResourceIndex(profile,map,collisions);for(Provider p:map.values())index.source(p.relativeSource());return index;}}

    private Path source(String relative)throws IOException{Path p=profileRoot.resolve(relative).normalize();if(!p.startsWith(profileRoot)||Files.isSymbolicLink(p)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Provider source invalid");Path realRoot=profileRoot.toRealPath(),real=p.toRealPath();if(!real.startsWith(realRoot))throw new IOException("Provider source escape");return real;}
    private static void put(Map<String,Provider> map,Set<String> collisions,String name,Provider p)throws IOException{if(map.put(name,p)!=null)collisions.add(name);if(map.size()>MAX_PROVIDERS)throw new IOException("Provider limit");}
    private static List<String> modOrder(Path file)throws IOException{List<String> result=new ArrayList<>();Set<String> seen=new TreeSet<>();for(String raw:new String(bounded(file,1024*1024),StandardCharsets.UTF_8).lines().toList()){String n=raw.trim();if(n.isEmpty()||n.startsWith("#"))continue;if(n.length()>255||n.equals(".")||n.equals("..")||n.contains("/")||n.contains("\\")||!seen.add(n))throw new IOException("Invalid mod order");result.add(n);}if(result.isEmpty()||result.size()>1000)throw new IOException("Mod order size");return result;}
    private static byte[] readJar(JarFile jar,java.util.jar.JarEntry e,String name)throws IOException{if(e==null||e.isDirectory()||e.getSize()>MAX_RESOURCE_BYTES)throw new IOException("Jar entry invalid "+name);try(InputStream in=jar.getInputStream(e)){byte[] b=in.readNBytes(MAX_RESOURCE_BYTES+1);if(b.length>MAX_RESOURCE_BYTES)throw new IOException("Jar entry limit");return b;}}
    private static byte[] bounded(Path file,int max)throws IOException{try(InputStream in=Files.newInputStream(file)){byte[] b=in.readNBytes(max+1);if(b.length>max)throw new IOException("File limit");return b;}}
    private static void str(DataOutputStream out,String s)throws IOException{byte[] b=s.getBytes(StandardCharsets.UTF_8);if(b.length>MAX_STRING_BYTES)throw new IOException("String limit");out.writeInt(b.length);out.write(b);}private static String read(DataInputStream in)throws IOException{int n=in.readInt();if(n<0||n>MAX_STRING_BYTES)throw new IOException("String size");byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();String s=new String(b,StandardCharsets.UTF_8);if(!Arrays.equals(b,s.getBytes(StandardCharsets.UTF_8)))throw new IOException("UTF-8");return s;}
    private static String logical(String value){Objects.requireNonNull(value);String n=value.replace('\\','/');if(n.startsWith("/")||n.matches("^[A-Za-z]:/.*")||n.isEmpty()||n.equals(".")||n.equals("..")||n.contains("//")||n.startsWith("../")||n.contains("/../")||n.endsWith("/..")||n.contains("/./")||n.endsWith("/."))throw new IllegalArgumentException("Invalid logical path "+value);return n;}
    private static String relative(String s){String n=logical(s);if(n.isEmpty())throw new IllegalArgumentException();return n;}private static String norm(Path p){return p.toString().replace('\\','/');}
    private static void hash(String h){if(h==null||!h.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("SHA-256");}private static MessageDigest sha(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}private static void update(MessageDigest d,String s){byte[] b=s.getBytes(StandardCharsets.UTF_8);d.update(ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}private static String msg(Throwable e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private static void atomic(Path target,byte[] bytes)throws IOException{Path p=target.toAbsolutePath().normalize();Files.createDirectories(p.getParent());Path tmp=p.resolveSibling(p.getFileName()+".tmp-"+ProcessHandle.current().pid()+"-"+System.nanoTime());boolean moved=false;try{Files.write(tmp,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);try{Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}moved=true;}finally{if(!moved)Files.deleteIfExists(tmp);}}
}
