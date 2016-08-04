import org.grouplens.lenskit.transform.normalize.MeanCenteringVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.grouplens.lenskit.transform.normalize.VectorNormalizer
import org.lenskit.api.ItemScorer
import org.lenskit.baseline.BaselineScorer
import org.lenskit.baseline.ItemMeanRatingItemScorer
import org.lenskit.baseline.UserMeanBaseline
import org.lenskit.baseline.UserMeanItemScorer
import org.lenskit.slopeone.SlopeOneItemScorer
import org.lenskit.slopeone.DeviationDamping


bind ItemScorer to SlopeOneItemScorer
bind (BaselineScorer,ItemScorer) to UserMeanItemScorer
bind (UserMeanBaseline,ItemScorer) to ItemMeanRatingItemScorer
set DeviationDamping to 0.0d



