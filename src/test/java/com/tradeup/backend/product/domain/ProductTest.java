package com.tradeup.backend.product.domain;

import com.tradeup.backend.member.domain.Member;
import com.tradeup.backend.global.exception.BusinessException;
import com.tradeup.backend.global.exception.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static final String PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("test-password");

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,false", "false,true,false", "false,false,true",
            "true,true,false", "true,false,true", "false,true,true", "true,true,true"})
    void Present가_true인_필드만_수정한다(boolean titlePresent, boolean descriptionPresent, boolean pricePresent) {
        Product product = productAt(ProductStatus.SELLING);
        Member seller = product.getSeller();
        // Non-null values with false flags must also be ignored.
        product.update(titlePresent, "변경 제목", descriptionPresent, "변경 설명", pricePresent, 0L);
        assertThat(product.getTitle()).isEqualTo(titlePresent ? "변경 제목" : "테스트 상품");
        assertThat(product.getDescription()).isEqualTo(descriptionPresent ? "변경 설명" : "테스트 설명");
        assertThat(product.getPrice()).isEqualTo(pricePresent ? 0L : 10000L);
        assertThat(product.getSeller()).isSameAs(seller);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 설명의_null과_빈_문자열은_명시적으로_반영한다(String description) {
        Product product = productAt(ProductStatus.SELLING);
        product.update(false, null, true, description, false, null);
        assertThat(product.getDescription()).isEqualTo(description);
        assertThat(product.getTitle()).isEqualTo("테스트 상품");
        assertThat(product.getPrice()).isEqualTo(10000L);
    }

    @Test
    void 판매중인_상품을_예약하면_상태가_RESERVED로_변경된다() {

        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        // when
        product.reserve();

        // then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);

    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"RESERVED", "SOLD"})
    void 판매중이_아닌_상품은_예약할_수_없다(ProductStatus status) {
        //given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        moveTo(product, status);
        ProductSnapshot before = snapshot(product);

        // when & then
        assertThatThrownBy(() -> product.reserve())
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("판매 중인 상품만 예약할 수 있습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));
        assertThat(snapshot(product)).isEqualTo(before);
    }

    @Test
    void 예약된_상품을_판매완료하면_상태가_SOLD로_변경된다() {
        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);

        product.reserve();

        // when
        product.sold();

        // then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"SELLING", "SOLD"})
    void 예약이_아닌_상품은_판매완료할_수_없다(ProductStatus status) {
        // given
        Member seller = new Member("seller@test.com", "seller", PASSWORD_HASH);
        Product product = new Product(seller, "테스트 상품", "테스트 설명", 10000L);
        moveTo(product, status);
        ProductSnapshot before = snapshot(product);

        // when & then
        assertThatThrownBy(() -> product.sold())
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("예약된 상품만 판매 완료할 수 있습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));
        assertThat(snapshot(product)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"RESERVED", "SOLD"})
    void 판매중이_아닌_상품은_수정할_수_없고_모든_필드가_보존된다(ProductStatus status) {
        Product product = productAt(status);
        ProductSnapshot before = snapshot(product);
        assertThatThrownBy(() -> product.update(true, "변경 제목", true, "변경 설명", true, 20000L))
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("판매 중인 상품만 수정할 수 있습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));
        assertThat(snapshot(product)).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"RESERVED", "SOLD"})
    void 판매중이_아닌_상품은_삭제_검증에_실패하고_필드가_보존된다(ProductStatus status) {
        Product product = productAt(status);
        ProductSnapshot before = snapshot(product);
        assertThatThrownBy(product::validateDeletable)
                .isExactlyInstanceOf(BusinessException.class)
                .hasMessage("판매 중인 상품만 삭제할 수 있습니다.")
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isSameAs(ErrorCode.CONFLICT));
        assertThat(snapshot(product)).isEqualTo(before);
    }

    @Test
    void 판매중인_상품은_수정되고_판매자와_상태는_유지된다() {
        Product product = productAt(ProductStatus.SELLING);
        Member seller = product.getSeller();
        product.update(true, "변경 제목", true, "변경 설명", true, 20000L);
        assertThat(product.getTitle()).isEqualTo("변경 제목");
        assertThat(product.getDescription()).isEqualTo("변경 설명");
        assertThat(product.getPrice()).isEqualTo(20000L);
        assertThat(product.getSeller()).isSameAs(seller);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SELLING);
    }

    @Test
    void 판매중인_상품은_삭제_검증을_통과하고_필드는_유지된다() {
        Product product = productAt(ProductStatus.SELLING);
        ProductSnapshot before = snapshot(product);
        product.validateDeletable();
        assertThat(snapshot(product)).isEqualTo(before);
    }

    private Product productAt(ProductStatus status) {
        Product product = new Product(new Member("seller@test.com", "seller", PASSWORD_HASH),
                "테스트 상품", "테스트 설명", 10000L);
        moveTo(product, status);
        return product;
    }

    private void moveTo(Product product, ProductStatus status) {
        if (status != ProductStatus.SELLING) product.reserve();
        if (status == ProductStatus.SOLD) product.sold();
        assertThat(product.getStatus()).isEqualTo(status);
    }

    private ProductSnapshot snapshot(Product product) {
        return new ProductSnapshot(product.getId(), product.getSeller(), product.getTitle(),
                product.getDescription(), product.getPrice(), product.getStatus());
    }

    private record ProductSnapshot(Long id, Member seller, String title, String description,
                                   Long price, ProductStatus status) {}

}
