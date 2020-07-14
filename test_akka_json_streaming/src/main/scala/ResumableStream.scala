import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util.concurrent.atomic.AtomicBoolean

import Domain.LatestStreamOffset
import akka.actor.typed.{ActorSystem, Extension, ExtensionId}

import scala.concurrent.{ExecutionContext, Future}

abstract class ResumableStream {
  def storeLatestOffset(offset:Long)(implicit ctxt: ExecutionContext):Future[Boolean]
  def fetchLatestOffset(implicit ctxt: ExecutionContext):Future[Option[Long]]
}


object ResumableStream{
  def apply(system:ActorSystem[_]) =
    new FileResumableStream(system)
}

class FileResumableStream(system:ActorSystem[_]) extends ResumableStream {
  val offsetStorage = FileOffsetStorage(system)

  def storeLatestOffset(offset:Long)(implicit ctxt: ExecutionContext):Future[Boolean] = {
    offsetStorage.updateOffset(offset).recover {
      case t => false
    }
  }
  def fetchLatestOffset(implicit ctxt: ExecutionContext):Future[Option[Long]] = {
    offsetStorage.fetchLatestOffset
  } recover{
    case t => None

  }
}

class FileOffsetStorageExt(system:ActorSystem[_]) extends Extension {


  //implicit val log = Logging(system.eventStream, "FileOffestStorage")

  var initialized = new AtomicBoolean(false)

  val fos = new FileOutputStream("./offset.db")
  val oos = new ObjectOutputStream(fos)

  val fis = new FileInputStream("./offset.db")
  val ois = new ObjectInputStream(fis)

  def updateOffset(offset:Long)(implicit ctxt: ExecutionContext): Future[Boolean] =  for {
    _ <- Future{oos.writeObject(LatestStreamOffset(offset))}
  } yield true



  def fetchLatestOffset(implicit ctxt: ExecutionContext): Future[Option[Long]] = for {
    out <- Future{ois.readObject}

  } yield {
    ois.close()
    Option(out.asInstanceOf[LatestStreamOffset].offset)
  }

}




object FileOffsetStorage extends ExtensionId[FileOffsetStorageExt] {

  def createExtension(system:  ActorSystem[_]) =
    new FileOffsetStorageExt(system)

  def get(system: ActorSystem[_]): FileOffsetStorageExt = apply(system)
}