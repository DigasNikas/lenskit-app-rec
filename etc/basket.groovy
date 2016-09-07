import org.grouplens.lenskit.transform.normalize.UnitVectorNormalizer
import org.grouplens.lenskit.transform.normalize.VectorNormalizer
import org.grouplens.lenskit.transform.normalize.ItemVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.lenskit.api.ItemBasedItemScorer
import org.lenskit.basic.ConstantItemScorer
import org.lenskit.basic.ConstantItemScorer.Value
import org.lenskit.baseline.BaselineScorer
import org.lenskit.baseline.UserMeanItemScorer
import org.lenskit.knn.MinNeighbors
import org.lenskit.knn.item.ItemItemItemBasedItemScorer
import org.lenskit.knn.item.SimilaritySumNeighborhoodScorer
import org.lenskit.knn.item.NeighborhoodScorer
import org.lenskit.knn.NeighborhoodSize

// ... and configure the item scorer.  The bind and set methods
// are what you use to do that. Here, we want an item-item scorer.
bind ItemBasedItemScorer to ItemItemItemBasedItemScorer
// Item-item works best with a minimum neighbor count
set MinNeighbors to 2
set NeighborhoodSize to 10

// let's use personalized mean rating as the baseline/fallback predictor.
// 2-step process:
// First, use the user mean rating as the baseline scorer
bind (BaselineScorer, ItemScorer) to UserMeanItemScorer

// and normalize ratings
within (UserVectorNormalizer) {
    bind VectorNormalizer to UnitVectorNormalizer
}
// Neighborhood scorer that computes the sum of neighborhood similarities.
// We don't want the Weighted Average
bind NeighborhoodScorer to SimilaritySumNeighborhoodScorer
