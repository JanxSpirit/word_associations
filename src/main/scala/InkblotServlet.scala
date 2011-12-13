import com.mongodb.casbah.commons.MongoDBObjectBuilder
import org.scalatra._
import com.mongodb.casbah.Imports._
import java.util.{TimerTask, Timer}

class InkblotServlet extends ScalatraServlet {

  //setup Casbah connection
  val wordList = List("foo","apple", "boring", "truly", "platinum", "strange", "missing", "wry", "hot", "blast", "stopper", "flight", "crew", "quench",
    "piper", "mild", "pile")
  val mongo_assoc = MongoConnection("localhost", 27017)("inkblot")("associations")
  val mongo_games = MongoConnection("localhost", 27017)("inkblot")("games")
  mongo_assoc.dropCollection
  mongo_games.dropCollection
  var currentWord: Int = 0
  var currentGameStart: Long = System.currentTimeMillis

  val gameTimer = new Timer
  gameTimer.schedule(new GameTask, 0, (30 * 1000))

  before() {
    response.setHeader("Access-Control-Allow-Origin", "*")
  }

  get("/associations/current") {
    contentType = "application/json"
    MongoDBObject("location" -> currentGame).toString
  }

  get("/associations/g/:game") {
    val user = request.cookies.get("user")
    val addUser = MongoDBObject(
      "user" -> user,
      "game" -> params("game"),
      "createdAt" -> System.currentTimeMillis()
    )
    mongo_assoc.findOne(MongoDBObject("user" -> user, "game" -> params("game"))) match {
      case None => mongo_assoc.insert(addUser)
      case _ =>
    }
    val r = MongoDBObject.newBuilder

    //game section
    val game = mongo_games.findOne(MongoDBObject("game" -> params("game"))).get
    r += ("game" -> params("game"))
    r += ("mode" -> game.getAs[String]("mode"))
    r += ("now" -> System.currentTimeMillis())
    r += ("gameStart" -> game.getAs[Long]("gameStart").getOrElse(System.currentTimeMillis()))
    r += ("gameEnd" -> game.getAs[Long]("gameEnd").getOrElse(System.currentTimeMillis()))
    if (isGameDone) r += ("nextGame" -> ("associations/g/"+(wordList(currentWord+1))))

    //guesses
    val all = mongo_assoc.find(MongoDBObject("game" -> params("game"))).toList
    val gts = all.flatMap(a => {
      if (a.getAs[String]("guess") != None) {
        Some(GuessTime(a.getAs[String]("guess").get, a.getAs[String]("user").get, a.getAs[Long]("createdAt").get))
      } else None
    })
    val builders: List[MongoDBObjectBuilder] = gts.sortWith((a, b) => a.guess < b.guess).groupBy(gt => gt.guess).map(gp => {
      val bongoed = gp._2.size > 1
      val builder = MongoDBObject.newBuilder
      builder += ("display" -> (if (bongoed) gp._1 else null))
      builder += ("user" -> (if (bongoed) {
        gp._2.sortWith((a, b) => a.createdAt < b.createdAt).head.user
      } else gp._2.head.user))
      if (bongoed) builder += ("matcher" -> gp._2(1).user)
      builder += ("updated" -> (if (bongoed) {
        gp._2(1).createdAt
      } else {
        gp._2(0).createdAt
      }))
    }).toList
    (0 until builders.size).foreach(i => {
      builders(i) += ("row" -> ("row" + i.toString))
    })
    val guesses = builders.map(_.result).toList

    //scoreboard
    val scoreboard = new scala.collection.mutable.HashMap[String, Int]
    guesses.filter(_.getAs[String]("matcher") != None).foreach(m => {
      if (scoreboard.containsKey(m.get("user").toString)) {
        val recu: Int = scoreboard.get(m.getAs[String]("user").get).get
        scoreboard.put((m.get("user").toString), (recu + 1))
      } else scoreboard.put(m.get("user").toString, 1)
      if (scoreboard.containsKey(m.get("matcher").toString)) {
        val recu: Int = scoreboard.get(m.getAs[String]("matcher").get).get
        scoreboard.put((m.get("matcher").toString), (recu + 1))
      } else scoreboard.put(m.get("matcher").toString, 1)
    })
    val scoreboards = scoreboard.map(row => {
      MongoDBObject(
        "user" -> row._1,
        "points" -> row._2,
        "arrived" -> gts.filter(_.user == row._1).sortWith((a, b) => a.createdAt < b.createdAt).head.createdAt
      )
    })

    r += ("guesses" -> guesses)
    r += ("scoreboard" -> scoreboards)
    contentType = "application/json"
    r.result().toString
  }

  post("/associations/g/:game/guesses") {
    val dbo = com.mongodb.util.JSON.parse(request.body).asInstanceOf[DBObject]
    val user = request.cookies.get("user")
    dbo.put("createdAt", System.currentTimeMillis)
    dbo.put("user", user)
    dbo.put("game", params("game"))
    mongo_assoc.insert(dbo)
    contentType = "application/json"
    ()
  }

  def isGameDone = {
    val q = MongoDBObject("game" ->  wordList(currentWord))
    val game = mongo_games.findOne(q)
    game match {
      case Some(game) => {
        (game.getAs[Long]("gameEnd").get < System.currentTimeMillis)
      }
      case None => false
    }
  }

  def currentGame = {
    synchronized {
      val q = MongoDBObject("game" -> wordList(currentWord))
      val game = mongo_games.findOne(q)
      game match {
        case Some(game) => {
          if ((game.getAs[Long]("gameEnd").get) > System.currentTimeMillis) {
            "associations/g/"+wordList(currentWord)
          } else {
            newGame
          }
        }
        case None => newGame
      }
    }
  }

  def newGame = {
    synchronized {
      //add a game
      currentWord = currentWord + 1
      val newGame = MongoDBObject(
        "game" -> wordList(currentWord),
        "mode" -> "rhyme",
        "gameStart" -> System.currentTimeMillis(),
        "gameEnd" -> (System.currentTimeMillis() +(30*1000))
      )
      mongo_games += newGame
      //return its location
      "associations/g/"+wordList(currentWord)
    }
  }

}

class GameTask extends TimerTask {
  def run() = {
    //fire new word notification message
  }
}

case class GuessTime(guess: String, user: String, createdAt: Long)


//TODO - make sure users can only make one given guess on a word