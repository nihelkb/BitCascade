// Clase que implementa la interfaz remota Seed.
// Actúa como un servidor que ofrece un método remoto para leer los bloques
// del fichero que se está descargando.
// Proporciona un método estático (init) para instanciarla.
// LA FUNCIONALIDAD SE COMPLETA EN LAS 4 FASES TODO 1, TODO 2, TODO 3 y TODO 4
// En las fases 3 y 4 se convertirá en un objeto remoto

package peers;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;

import interfaces.FileInfo;
import interfaces.Leech;
import interfaces.Seed;
import interfaces.Tracker;


// Un Leech guarda en esta clase auxiliar su conocimiento sobre cuál es el
// último bloque descargado por otro Leech.
class LeechInfo {
    Leech leech;    // de qué Leech se trata
    int lastBlock;  // último bloque que sabemos que se ha descargado
    public LeechInfo (Leech l, int nBl) {
        leech = l;
        lastBlock = nBl;
    }
    Leech getLeech() {
        return leech;
    }
    int getLastBlock() {
        return lastBlock;
    }
    void setLastBlock(int nBl) {
        lastBlock = nBl;
    }
}
// Esta clase actúa solo de cliente en las dos primeras fases, pero
// en las dos últimas ejerce también como servidor convirtiéndose en
// un objeto remoto.
public class DownloaderImpl extends UnicastRemoteObject implements Leech { 
    public static final long serialVersionUID=1234567890L;
    String name; // nombre del nodo (solo para depurar)
    String file;
    String path;
    int blockSize;
    int numBlocks;
    int lastBlock = -1; // último bloque descargado por este Leech
    Seed seed;
    FileInfo fInfo;
    transient RandomAccessFile raf;
    LeechInfo leechList[];
    int order = 0;
    long fileSizeBytes;

    public DownloaderImpl(String n, String f, FileInfo finf) throws RemoteException, IOException {
        name = n;
        file = f;
        path = name + "/" + file;
        blockSize = finf.getBlockSize();
        numBlocks = finf.getNumBlocks();
        fileSizeBytes = new File(path).length();
        seed = finf.getSeed();
        fInfo = finf;
        // TODO 2: abre el fichero para escritura
        raf = new RandomAccessFile(path, "rw");
        raf.setLength(0);

        // TODO 3: obtiene el número del último bloque descargado por leeches
	    // anteriores (contenidos en FileInfo) usando getLastBlockNumber
        LinkedList<Leech> fileLeeches = fInfo.getLeechList();
        int nLeeches = fileLeeches.size();
        leechList = new LeechInfo [nLeeches];
        for(int i = 0; i < nLeeches; i++){
            leechList[i] = new LeechInfo(fileLeeches.get(i), fileLeeches.get(i).getLastBlockNumber());
        }
    

        // TODO 4: solicita a esos leeches anteriores usando newLeech
        // que le notifiquen cuando progrese su descarga
    }
    /* métodos locales */
    public int getNumBlocks() {
        return numBlocks;
    }
    public FileInfo getFileInfo() {
        return fInfo;
    }

    boolean siSePuede(int numBl){
        boolean res = false;
        for(int i = 0; i< leechList.length && !res; i++){
            System.out.println(leechList[i].getLastBlock()+"\n");
            if(numBl < leechList[i].getLastBlock()) res = true;
        }
        return res;
    }
    // realiza la descarga de un bloque y lo almacena en un fichero local
    public boolean downloadBlock(int numBl) throws RemoteException {
        // TODO 3: Alterna leer bloques del seed y de otros leeches
        int nHosts = leechList.length + 1;
        //          0 1 2 seed 0 1 2 seed
        //order     0 1 2  3   4
        //turno     0 1 2  3   0
        byte [] buf = null;
        boolean hecho = false;
        if(siSePuede(numBl)){ 
            while(siSePuede(numBl) && !hecho){
                int turno = order % nHosts;
                if(turno == leechList.length){
                    System.out.println("Imprimiendo del seed");
                    buf = seed.read(numBl);
                    hecho = true;
                }else{
                    LeechInfo leechInf = leechList[turno];
                    if(leechInf.getLastBlock() > numBl){
                        System.out.println("Imprimiendo de " + leechInf.getLeech().getName());
                        buf = leechInf.getLeech().read(numBl);
                        hecho = true;
                    }
                }
                order++;
            }
        }else{
            System.out.println("Imprimiendo del seed");
            buf = seed.read(numBl);
        }
        
        // TODO 2: Lee bloque del seed y lo escribe en el fichero
        //byte [] buf = seed.read(numBl);
        if (buf == null) return false;
        try {
            raf.seek(numBl * blockSize);
            raf.write(buf);
            setLastBlockNumber();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
	    // TODO 4: Notifica a los leeches posteriores (notifyBlock)
        return true;
    }

    /* métodos remotos que solo se usarán cuando se convierta en
       un objeto remoto en la fase 3 */
 
    // solo para depurar
    public String getName() throws RemoteException {
        return name;
    }
    // prácticamente igual que read del Seed
    public byte [] read(int numBl) throws RemoteException {
        byte [] buf = null;
        System.out.println("downloader read " + numBl);
        // TODO 3: realiza lectura solicitada devolviendo lo leído en buf 
        // Cuidado con último bloque que probablemente no estará completo

        if (numBl < numBlocks) {
            int bufSize = blockSize;
            
            if (numBl + 1 == numBlocks) { // último bloque
                int fragmentSize = (int) (fileSizeBytes % blockSize);
                if (fragmentSize > 0) bufSize = fragmentSize;
            }
            buf = new byte[bufSize];
            int n = 0;
            try {
                raf.seek(numBl * blockSize);
                n = raf.read(buf);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            System.out.println("Bytes leídos = " + n);
        }
        //
        return buf;
    } 
    // obtiene cuál es el último bloque descargado por este Leech
    public int getLastBlockNumber() throws RemoteException{
        return lastBlock;
    }

    public int setLastBlockNumber() throws RemoteException{
        return lastBlock++;
    }

    /* métodos remotos solo para la última fase */
    // leech solicitante será notificado del progreso de la descarga
    public void newLeech(Leech requester) throws RemoteException {
        // TODO 4: añade ese leech a la lista de leeches posteriores
	// que deben ser notificados
    }
    // Informa del progreso de la descarga
    public void notifyBlock(Leech l, int nBl) throws RemoteException {
        // TODO 4: actualizamos la información sobre el último bloque
	// descargado por ese leech
    }

    // método estático que obtiene del registry una referencia al tracker y
    // obtiene mediante lookupFile la información del fichero especificado
    // creando una instancia de esta clase
    static public DownloaderImpl init(String host, int port, String name, String file) throws RemoteException {
        if (System.getSecurityManager() == null)
            System.setSecurityManager(new SecurityManager());

        DownloaderImpl down = null;
        try {
            // TODO 1: localiza el registry en el host y puerto indicado
            Registry registry = LocateRegistry.getRegistry(host, port);
            // y obtiene la referencia remota al tracker asignándola
            // a esta variable:
            Tracker trck = (Tracker) registry.lookup("BitCascade");

            // comprobamos si ha obtenido bien la referencia:
            System.out.println("el nombre del nodo del tracker es: " + trck.getName());
            // TODO 1: obtiene la información del fichero mediante el
	    // método lookupFile del Tracker.
            FileInfo finf = trck.lookupFile(file); // asigna resultado de lookupFile
            if (finf==null) { // comprueba resultado
                // si null: no se ha publicado ese fichero
                System.err.println("Fichero no publicado");
                System.exit(1);
            }
            // TODO 1: crea un objeto de la clase DownloaderImpl
            down = new DownloaderImpl(name, file, finf);


            // TODO 3: usa el método addLeech del tracker para añadirse
            trck.addLeech(down, file);
        }
        catch (Exception e) {
            System.err.println("Downloader exception:");
            e.printStackTrace();
            System.exit(1);
        }
        return down;
    }
}
