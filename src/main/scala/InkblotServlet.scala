import org.scalatra._
import com.mongodb.casbah.Imports._
import java.util.Date

class InkblotServlet extends ScalatraServlet {

  //setup Casbah connection
  val wordList = ("apple", "disingenuous", "unlimited", "platinum")
  val mongo_assoc = MongoConnection("localhost", 27017)("inkblot")("associations")
  var currentWord: Int = 0

  post("/words/associations") {
    val dbo = com.mongodb.util.JSON.parse(request.body).asInstanceOf[DBObject]
    dbo.put("createdAt", new Date)
    mongo_assoc.insert(dbo)
    mongo_assoc.getLastError()
  }

  get("/users/:game") {
    val dbo = MongoDBObject("game" -> params("game"))
    val userList = mongo_assoc.distinct("user", dbo)
    val result = MongoDBObject
    MongoDBObject("users" -> userList.map(u => {
      val udbo = MongoDBObject("user" -> u)
    })).toString
  }

}
