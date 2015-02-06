import org.apache.spark.mllib.clustering.HierarchicalClustering
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.uncommons.maths.random.XORShiftRNG


object HierarchicalClusteringApp {

  def main(args: Array[String]) {

    val master = args(0)
    val rows = args(1).toInt
    val cores = args(2)
    val numClusters = args(3).toInt
    val dimension = args(4).toInt
    val numPartitions = args(5).toInt

    val conf = new SparkConf()
        .setAppName("hierarchical clustering")
        .setMaster(master)
        .set("spark.cores.max", cores)
        .set("spark.driver.allowMultipleContexts", "true")
    val sc = new SparkContext(conf)

    val denseVectors = generateDenseVectors(numClusters, dimension)
    val denseTestData = generateDenseData(sc, rows, numPartitions, denseVectors)
    val denseData = denseTestData.map(_._2)

    val trainStart = System.currentTimeMillis()
    val model = HierarchicalClustering.train(denseData, numClusters)
    val trainEnd = System.currentTimeMillis() - trainStart

    sc.broadcast(denseVectors)
    sc.broadcast(model)
    val distances = denseTestData.map { case (idx, point) =>
      val origin = denseVectors(idx)
      val diff = point.toArray.zip(origin.toArray).map { case (a, b) => (a - b) * (a - b)}.sum
      math.pow(diff, origin.size)
    }
    val failuars = distances.filter(_ > 10E-5).count


    println(s"====================================")
    println(s"Elapse Training Time: ${trainEnd / 1000.0} [sec]")
    println(s"cores: ${cores}")
    println(s"rows: ${denseData.count}")
    println(s"numClusters: ${numClusters}")
    println(s"dimension: ${dimension}")
    println(s"numPartition: ${numPartitions}")
    println(s"# Different Points: ${failuars}")
  }

  def generateDenseVectors(numCenters: Int, dimension: Int): Array[Vector] = {
    val rand = new XORShiftRNG()
    (1 to numCenters).map { i =>
      val elements = (1 to dimension).map(i => 1000 * rand.nextDouble()).toArray
      Vectors.dense(elements)
    }.toArray
  }

  def generateDenseData(
    sc: SparkContext,
    rows: Int,
    numPartitions: Int,
    centers: Array[Vector]): RDD[(Int, Vector)] = {

    val seeds = sc.parallelize(1 to rows, numPartitions)
    val random = new XORShiftRNG()
    sc.broadcast(random)
    seeds.map { i =>
      val idx = (i % centers.size).toInt
      val elements = centers(idx).toArray.map(elm => elm + (elm * 0.0001 * random.nextGaussian()))
      (idx, Vectors.dense(elements))
    }
  }
}
