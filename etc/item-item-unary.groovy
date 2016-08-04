import org.grouplens.lenskit.transform.normalize.UnitVectorNormalizer
import org.grouplens.lenskit.transform.normalize.VectorNormalizer
import org.grouplens.lenskit.transform.normalize.ItemVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.lenskit.api.ItemScorer
import org.lenskit.baseline.BaselineScorer
import org.lenskit.basic.ConstantItemScorer
import org.lenskit.basic.ConstantItemScorer.Value
import org.lenskit.knn.MinNeighbors
import org.lenskit.knn.NeighborhoodSize
import org.lenskit.knn.item.ItemItemScorer
import org.lenskit.knn.item.SimilaritySumNeighborhoodScorer
import org.lenskit.knn.item.NeighborhoodScorer

// ... and configure the item scorer.  The bind and set methods
// are what you use to do that. Here, we want an item-item scorer.
bind ItemScorer to ItemItemScorer.class
// Item-item works best with a minimum neighbor count
set MinNeighbors to 2

// let's use personalized mean rating as the baseline/fallback predictor.
bind (BaselineScorer, ItemScorer) to ConstantItemScorer
set ConstantItemScorer.Value to 0

// and normalize ratings
within (ItemVectorNormalizer) {
    bind VectorNormalizer to UnitVectorNormalizer
}
// Neighborhood scorer that computes the sum of neighborhood similarities.
// We don't want the Weighted Average
bind NeighborhoodScorer to SimilaritySumNeighborhoodScorer
