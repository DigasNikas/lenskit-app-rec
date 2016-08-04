import org.grouplens.lenskit.transform.normalize.MeanCenteringVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.grouplens.lenskit.transform.normalize.VectorNormalizer
import org.lenskit.api.ItemScorer
import org.lenskit.baseline.BaselineScorer
import org.lenskit.baseline.ItemMeanRatingItemScorer
import org.lenskit.baseline.UserMeanBaseline
import org.lenskit.baseline.UserMeanItemScorer
import org.lenskit.knn.NeighborhoodSize
import org.lenskit.knn.user.UserUserItemScorer

// ... and configure the item scorer.  The bind and set methods
// are what you use to do that. Here, we want an item-item scorer.
bind ItemScorer to UserUserItemScorer

// let's use personalized mean rating as the baseline/fallback predictor.
// 2-step process:
// First, use the user mean rating as the baseline scorer
bind (BaselineScorer, ItemScorer) to UserMeanItemScorer
// Second, use the item mean rating as the base for user means
bind (UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer

within (UserVectorNormalizer) {
    // for normalization, just center on user means
  bind VectorNormalizer to MeanCenteringVectorNormalizer
}
set NeighborhoodSize to 30
